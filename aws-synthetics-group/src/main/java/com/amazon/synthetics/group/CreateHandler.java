package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.CreateGroupRequest;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
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
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        if (!callbackContext.isGroupCreationStarted()) {
            // Creation has yet to begin
            log("Creating Group.");
            callbackContext.setGroupCreationStarted(true);

            return createGroup();
        }
        if (model.getResourceArns() == null || callbackContext.getAddResourceListIndex() == model.getResourceArns().size()) {
            log(Constants.GROUP_CREATION_SUCCESSFUL);
            return ProgressEvent.defaultSuccessHandler(model);
        }

        if (!callbackContext.isGroupAssociationStarted()) {
            callbackContext.setGroupAssociationStarted(true);
            callbackContext.setAddResourceListIndex(0);
        }
        return addAssociatedResources(false);
    }

    /**
     * Wrapper around create Group api call to Synthetics client and handle the response/ error
     * @return success or in progress event depending on presence or absence of resourcearns
     */
    private ProgressEvent<ResourceModel, CallbackContext> createGroup() {
        // Translate resource model to create group request
        // call create group request
        log(Constants.MAKING_CREATE_GROUP);
        try {
            CreateGroupRequest createGroupRequest = Translator.translateToCreateRequest(model);
            webServiceProxy.injectCredentialsAndInvokeV2(createGroupRequest,  proxyClient.client()::createGroup);
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
