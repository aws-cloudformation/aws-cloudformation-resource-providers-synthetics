package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    public UpdateHandler() {
        super(Action.UPDATE);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        try {
            if (!callbackContext.isGroupUpdateStarted()) {
                // We just need the group at the start to get current arn list, we will rely on exceptions from AssociateResource to handle concurrent mods
                // This will need to be revisited when we increase resource limit
                log("Started update request");
                Group group = getGroupOrThrow();
                List<String> groupResources = getGroupResourcesOrThrow();
                diffGroupArnList(groupResources);
                if (model.getTags() != null) {
                    Map<String, Map<String, String>> tagResourceMap = TagHelper.updateTags(model, group.tags());
                    String groupArn = group.arn();
                    if (!tagResourceMap.get(Constants.ADD_TAGS).isEmpty()) {
                        addTags(tagResourceMap, groupArn);
                    }

                    if (!tagResourceMap.get(Constants.REMOVE_TAGS).isEmpty()) {
                        removeTags(tagResourceMap, groupArn);
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

            if (callbackContext.getAddResourceListIndex() == callbackContext.getAddResourceList().size() &&
                callbackContext.getRemoveResourceListIndex() == callbackContext.getRemoveResourceList().size()) {
                return ProgressEvent.defaultSuccessHandler(model);
            }

            if (!callbackContext.isGroupAssociationStarted()) {
                callbackContext.setGroupAssociationStarted(true);
            }

            if (callbackContext.isGroupAssociationStarted() &&
                callbackContext.getAddResourceListIndex() < callbackContext.getAddResourceList().size()) {
                return addAssociatedResources(true);
            }

            if (callbackContext.isGroupAssociationStarted() && !callbackContext.isGroupRemoveAssociationStarted() &&
                callbackContext.getAddResourceListIndex() >= callbackContext.getAddResourceList().size()) {
                callbackContext.setGroupRemoveAssociationStarted(true);
            }

            return removeAssociatedResources();
        } catch (CfnResourceConflictException e) {
            return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
        }
    }

    /**
     * Wrapper around tagResource call for Synthetics api and handle response/ error
     * @param tagResourceMap
     * @param groupArn
     */
    private void addTags(Map<String, Map<String, String>> tagResourceMap, String groupArn) {
        try {
            log(Constants.TAG_RESOURCE_CALL);
            TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                .resourceArn(groupArn)
                .tags(tagResourceMap.get(Constants.ADD_TAGS))
                .build();
            webServiceProxy.injectCredentialsAndInvokeV2(tagResourceRequest, proxyClient.client()::tagResource);
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
    private void removeTags(Map<String, Map<String, String>> tagResourceMap, String groupArn) {
        try {
            log(Constants.UNTAG_RESOURCE_CALL);
            UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
                .resourceArn(groupArn)
                .tagKeys(tagResourceMap.get(Constants.REMOVE_TAGS).keySet())
                .build();
            webServiceProxy.injectCredentialsAndInvokeV2(untagResourceRequest, proxyClient.client()::untagResource);
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
     * @param groupResources
     */
    @VisibleForTesting
    private void diffGroupArnList(List<String> groupResources) {
        List<String> addResourceList = new ArrayList<>();
        List<String> removeResourceList = new ArrayList<>(groupResources);
        callbackContext.setAddResourceList(addResourceList);
        callbackContext.setAddResourceListIndex(0);
        callbackContext.setRemoveResourceList(removeResourceList);
        callbackContext.setRemoveResourceListIndex(0);
        if (model.getResourceArns() == null || model.getResourceArns().isEmpty()) {
            return;
        }
        addResourceList = new ArrayList<>(model.getResourceArns());
        log("Group resources in model: " + model.getResourceArns().size());
        if (groupResources == null || groupResources.isEmpty()) {
            callbackContext.setAddResourceList(addResourceList);
            return;
        }
        log("Group resources previous: " + groupResources.size());
        removeResourceList.removeAll(model.getResourceArns());
        addResourceList.removeAll(groupResources);
        callbackContext.setAddResourceList(addResourceList);
        callbackContext.setRemoveResourceList(removeResourceList);
    }


}
