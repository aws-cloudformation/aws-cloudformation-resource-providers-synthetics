package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.BadRequestException;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.InternalFailureException;
import software.amazon.awssdk.services.synthetics.model.NotFoundException;
import software.amazon.awssdk.services.synthetics.model.TagResourceRequest;
import software.amazon.awssdk.services.synthetics.model.TooManyRequestsException;
import software.amazon.awssdk.services.synthetics.model.UntagResourceRequest;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {

    public UpdateHandler() {
        super(Action.UPDATE);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
            final ProxyClient<SyntheticsClient> proxyClient,
            final Logger logger) {
        try {
            ResourceModel model = request.getDesiredResourceState();

            if (!callbackContext.isGroupUpdateStarted()) {
                // We just need the group at the start to get current arn list, we will rely on exceptions from AssociateResource to handle concurrent mods
                // This will need to be revisited when we increase resource limit
                logger.log("Started update request");
                Group group = getGroupOrThrow(proxy, proxyClient, model, logger);
                List<String> groupResources = getGroupResourcesOrThrow(proxy, proxyClient, model, logger);
                diffGroupArnList(groupResources, callbackContext, model, logger);
                if (model.getTags() != null) {
                    Map<String, Map<String, String>> tagResourceMap = TagHelper.updateTags(model, group.tags());
                    String groupArn = group.arn();
                    if (!tagResourceMap.get(Constants.ADD_TAGS).isEmpty()) {
                        addTags(tagResourceMap, groupArn, proxy, proxyClient, logger);
                    }

                    if (!tagResourceMap.get(Constants.REMOVE_TAGS).isEmpty()) {
                        removeTags(tagResourceMap, groupArn, proxy, proxyClient, logger);
                    }
                }
                callbackContext.setGroupUpdateStarted(true);
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .callbackContext(callbackContext)
                    .callbackDelaySeconds(Constants.DEFAULT_CALLBACK_DELAY_SECONDS)
                    .message(Constants.GROUP_UPDATE_DIFF_COMPLETE)
                    .status(OperationStatus.IN_PROGRESS)
                    .build();
            }

            if (callbackContext.getAddResourceList() != null &&
                callbackContext.getAddResourceListIndex() < callbackContext.getAddResourceList().size()) {
                if (!callbackContext.isGroupAssociationStarted()) {
                    callbackContext.setGroupAssociationStarted(true);
                }
 
                return addAssociatedResources(true, proxy, callbackContext, proxyClientMap, model, logger);
            }

            if (callbackContext.getRemoveResourceList() != null &&
                callbackContext.getRemoveResourceListIndex() < callbackContext.getRemoveResourceList().size()) {
                if (!callbackContext.isGroupRemoveAssociationStarted()) {
                    callbackContext.setGroupRemoveAssociationStarted(true);
                }
 
                return removeAssociatedResources(proxy, callbackContext, proxyClientMap, model, logger);
            }

            return ProgressEvent.defaultSuccessHandler(model);
        } catch (CfnResourceConflictException e) {
            return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
        }
    }

    /**
     * Wrapper around tagResource call for Synthetics api and handle response/ error
     * @param tagResourceMap
     * @param groupArn
     */
    private void addTags(Map<String, Map<String, String>> tagResourceMap, 
            String groupArn, 
            AmazonWebServicesClientProxy proxy, 
            ProxyClient<SyntheticsClient> proxyClient,
            Logger logger) {
        try {
            logger.log(Constants.TAG_RESOURCE_CALL);
            TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                .resourceArn(groupArn)
                .tags(tagResourceMap.get(Constants.ADD_TAGS))
                .build();
            proxy.injectCredentialsAndInvokeV2(tagResourceRequest, proxyClient.client()::tagResource);
        }catch (BadRequestException | TooManyRequestsException | ConflictException | InternalFailureException e) {
            throw new CfnGeneralServiceException(e);
        } catch (NotFoundException e) {
            throw new CfnResourceConflictException(e);
        }
    }

    /**
     * Wrapper around untagResource call for Synthetics api and handle response/ error
     * @param tagResourceMap
     * @param groupArn
     */
    private void removeTags(
            Map<String, Map<String, String>> tagResourceMap, 
            String groupArn, 
            AmazonWebServicesClientProxy proxy,
            ProxyClient<SyntheticsClient> proxyClient,
            Logger logger) {
        try {
            logger.log(Constants.UNTAG_RESOURCE_CALL);
            UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
                .resourceArn(groupArn)
                .tagKeys(tagResourceMap.get(Constants.REMOVE_TAGS).keySet())
                .build();
            proxy.injectCredentialsAndInvokeV2(untagResourceRequest, proxyClient.client()::untagResource);
        }catch (BadRequestException | TooManyRequestsException | ConflictException | InternalFailureException e) {
            throw new CfnGeneralServiceException(e);
        } catch (NotFoundException e) {
            throw new CfnResourceConflictException(e);
        }
    }


    /**
     * Takes in a list of existing Resource values, compares them to the ones supplied in the model and creates 2 lists:
     * addResourceList: values in resource model not in current resources
     * removedResourceList: Values in current resource not in resource model
     * @param currentResourceArns
     */
    @VisibleForTesting
    private void diffGroupArnList(
            List<String> currentResourceArns,
            CallbackContext callbackContext,
            ResourceModel model,
            Logger logger) {
        List<String> requestResourceArns = model.getResourceArns();
        logger.log("Number of group resources in request: " + requestResourceArns.size());
 
        List<String> removeResourceList = new ArrayList<>(currentResourceArns);
        removeResourceList.removeAll(requestResourceArns);
        requestResourceArns.removeAll(currentResourceArns);
        callbackContext.setAddResourceList(requestResourceArns);
        callbackContext.setRemoveResourceList(removeResourceList);
        
        logger.log("Number of group resources to add: " + requestResourceArns.size());
        logger.log("Number of group resources currently: " + currentResourceArns.size());
    }


}
