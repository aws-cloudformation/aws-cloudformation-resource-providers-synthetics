package com.amazon.synthetics.group;

import java.util.Map;

import com.amazon.synthetics.group.Utils.Constants;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.CreateGroupRequest;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.*;


public class CreateHandler extends BaseHandlerStd {

    public CreateHandler() {
        super(Action.CREATE);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
        final ProxyClient<SyntheticsClient> proxyClient,
        final Logger logger) {
            
    ResourceModel model = request.getDesiredResourceState();
        logger.log(Constants.GROUP_CREATION_SUCCESSFUL);
        if (!callbackContext.isGroupCreationStarted()) {
            // Creation has yet to begin
            logger.log("Creating Group.");
            callbackContext.setGroupCreationStarted(true);

            return createGroup(proxy, request, callbackContext, proxyClient, logger);
        }
        if (model.getResourceArns() == null || callbackContext.getAddResourceListIndex() == model.getResourceArns().size()) {
            
            return ProgressEvent.defaultSuccessHandler(model);
        }

        if (!callbackContext.isGroupAssociationStarted()) {
            callbackContext.setGroupAssociationStarted(true);
            callbackContext.setAddResourceListIndex(0);
        }
        return addAssociatedResources(false, proxy, callbackContext, proxyClientMap, model, logger);
    }

    /**
     * Wrapper around create Group api call to Synthetics client and handle the response/ error
     * @return success or in progress event depending on presence or absence of resourcearns
     */
    private ProgressEvent<ResourceModel, CallbackContext> createGroup(
            AmazonWebServicesClientProxy proxy,
            ResourceHandlerRequest<ResourceModel> request,
            CallbackContext callbackContext, 
            ProxyClient<SyntheticsClient> proxyClient,
            Logger logger) {
        // Translate resource model to create group request
        // call create group request
        ResourceModel model = request.getDesiredResourceState();
 
        logger.log(Constants.MAKING_CREATE_GROUP);
        try {
            CreateGroupRequest createGroupRequest = Translator.translateToCreateRequest(model);
            proxy.injectCredentialsAndInvokeV2(createGroupRequest,  proxyClient.client()::createGroup);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (ConflictException e) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, e.getMessage(), e);
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }
        if (model.getResourceArns() == null || model.getResourceArns().size() == 0) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .callbackContext(callbackContext)
            .resourceModel(model)
            .status(OperationStatus.IN_PROGRESS)
            .callbackDelaySeconds(Constants.DEFAULT_CALLBACK_DELAY_SECONDS)
            .build();
    }
}
