package com.amazon.synthetics.canary;

import com.amazonaws.AmazonServiceException;
import software.amazon.awssdk.services.cloudwatch.model.ConcurrentModificationException;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_SECONDS = 5;
    private static final int MAX_RETRY_TIMES = 4; // 2min * 60 / 30 = 4
    Logger logger;
    private AmazonWebServicesClientProxy clientProxy;
    private ResourceHandlerRequest<ResourceModel> request;
    private SyntheticsClient syntheticsClient;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        this.clientProxy = proxy;
        this.request = request;
        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();
        final CallbackContext currentContext = callbackContext == null ?
                CallbackContext.builder()
                        .build() :
                callbackContext;
        syntheticsClient = ClientBuilder.getClient();

        // This Lambda will continually be re-invoked with the current state of the instance, finally succeeding when state stabilizes.
        return deleteCanaryAndUpdateProgress(model, currentContext, proxy, request, syntheticsClient);
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteCanaryAndUpdateProgress(final ResourceModel model,
                                                                                        final CallbackContext callbackContext,
                                                                                        final AmazonWebServicesClientProxy proxy,
                                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                                        final SyntheticsClient syntheticsClient) {
        // Start delete canary action
        if (!callbackContext.isCanaryDeleteStarted()) {
            return deleteCanary(model, callbackContext, proxy, request, syntheticsClient);
        }

        //Canary deletion has started. Wait for deletion to stabilize
        if (callbackContext.isCanaryDeleteStarted() && !callbackContext.isCanaryDeleteStabilized()) {
            return updateCanaryDeleteProgress(model, callbackContext, proxy, request, syntheticsClient);
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        final GetCanaryResponse getCanaryResponse;
        // Validate if canary that is to be deleted is in the correct state, else throw error
        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder().name(model.getName()).build();

        final DeleteCanaryRequest deleteCanaryRequest = DeleteCanaryRequest.builder()
                .name(model.getName())
                .build();
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest, syntheticsClient::getCanary);
            String canaryState = getCanaryResponse.canary().status().stateAsString();
            if (canaryState.compareTo(CanaryStates.RUNNING.toString()) == 0) {
                throw new CfnInvalidRequestException("Canary is in " + canaryState + "state and cannot be deleted. Please stop the canary before deletion");
            }
            proxy.injectCredentialsAndInvokeV2(deleteCanaryRequest, syntheticsClient::deleteCanary);
        } catch (final ResourceNotFoundException resourceNotFoundException) {
            logger.log(String.format("%s [%s] is already deleted",
                    ResourceModel.TYPE_NAME, model.getName()));
            return ProgressEvent.defaultSuccessHandler(null);
        }
        catch (final ConcurrentModificationException e) {
            throw new AmazonServiceException(e.getMessage());
        } catch (final ValidationException e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }

        callbackContext.setCanaryDeleteStarted(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryDeleteProgress(final ResourceModel model,
                                                                                     final CallbackContext callbackContext,
                                                                                     final AmazonWebServicesClientProxy proxy,
                                                                                     final ResourceHandlerRequest<ResourceModel> request,
                                                                                     final SyntheticsClient syntheticsClient
    ) {
        boolean canaryDeletionState = checkDeleteStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryCreationStablized(canaryDeletionState);
        callbackContext.incrementRetryTimes();
        OperationStatus operationStatus;

        if (canaryDeletionState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        } else {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
                    .resourceModel(model)
                    .status(OperationStatus.IN_PROGRESS)
                    .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                    .build();
        }
    }

    private boolean checkDeleteStabilization(final ResourceModel model,
                                             final AmazonWebServicesClientProxy proxy,
                                             final CallbackContext callbackContext,
                                             final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder().name(model.getName()).build();
        try {
            proxy.injectCredentialsAndInvokeV2(getCanaryRequest, syntheticsClient::getCanary);
        } catch (final ResourceNotFoundException e) {
            // If canary is not found, then it has been successfully deleted.
            logger.log("Canary: " + getCanaryRequest.name() + "not found and is successfully deleted");
            return true;
        }
        return false;
    }
}
