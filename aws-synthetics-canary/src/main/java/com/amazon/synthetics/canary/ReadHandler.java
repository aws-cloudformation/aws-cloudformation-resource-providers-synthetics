package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryResponse;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.*;

public class ReadHandler extends BaseHandler<CallbackContext> {
    private SyntheticsClient syntheticsClient;
    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        GetCanaryResponse getCanaryResponse = null;

        syntheticsClient = ClientBuilder.getClient();

        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest, syntheticsClient::getCanary);
            logger.log(String.format("%s [%s] GetCanary was successful", ResourceModel.TYPE_NAME, model.getName()));
        } catch (ResourceNotFoundException ex) {
            logger.log(
                    String.format("%s [%s] ResourceNotFound exception while GetCanary API was called", ResourceModel.TYPE_NAME, model.getName()));
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getName());
        } catch (ValidationException ex) {
            logger.log(String.format("%s [%s] GetCanary Failed", ResourceModel.TYPE_NAME, model.getName()));
        } catch (Exception ex) {
            logger.log(String.format("%s [%s] GetCanary Failed", ResourceModel.TYPE_NAME, model.getName()));
        }
        Canary canary = getCanaryResponse.canary();
        ResourceModel outputModel = ModelHelper.constructModel(canary, model);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(outputModel)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
