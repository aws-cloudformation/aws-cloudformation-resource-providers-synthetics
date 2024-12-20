package com.amazon.synthetics.group;

import java.util.Map;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.model.DeleteGroupRequest;
import software.amazon.awssdk.services.synthetics.model.InternalServerException;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {

    public DeleteHandler() {
        super(Action.DELETE);
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

        DeleteGroupRequest deleteGroupRequest = Translator.translateToDeleteRequest(model);
        try {
            proxy.injectCredentialsAndInvokeV2(deleteGroupRequest, proxyClient.client()::deleteGroup);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .status(OperationStatus.SUCCESS)
                .build();
        } catch (ResourceNotFoundException e) {
            return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
        } catch (InternalServerException exception) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME, exception);
        } catch (ValidationException exception) {
            throw new CfnInvalidRequestException(exception);
        }
    }

}
