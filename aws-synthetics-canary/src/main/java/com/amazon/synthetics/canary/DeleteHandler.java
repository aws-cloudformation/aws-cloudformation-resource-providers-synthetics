package com.amazon.synthetics.canary;

import com.amazonaws.AmazonServiceException;
import software.amazon.awssdk.services.cloudwatch.model.ConcurrentModificationException;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 10;
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
        //Check if the Canary is in the correct state for deletion
        if (!callbackContext.isCanaryStopStarted()) {
            return checkCanaryStateForDeletion(model, callbackContext, proxy, request, syntheticsClient);
        }

        if (callbackContext.isCanaryStopStarted() && !callbackContext.isCanaryStopStabilized()) {
            return updateCanaryStopProgress(model, callbackContext, proxy, request, syntheticsClient);
        }
        // Start delete canary action
        if (callbackContext.isCanaryStopStabilized() && !callbackContext.isCanaryDeleteStarted()) {
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
            Canary canary = getCanaryResponse.canary();
            // At this point, Canary is already in the STOPPED or READY state for deletion
            proxy.injectCredentialsAndInvokeV2(deleteCanaryRequest, syntheticsClient::deleteCanary);
            callbackContext.setCanaryDeleteStarted(true);
        } catch (final ResourceNotFoundException resourceNotFoundException) {
            logger.log(String.format("%s [%s] is not found",
                    ResourceModel.TYPE_NAME, model.getName()));
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier().toString());
        } catch (final ConcurrentModificationException e) {
            throw new AmazonServiceException(e.getMessage());
        } catch (final ValidationException e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> checkCanaryStateForDeletion(final ResourceModel model,
                                                                                      final CallbackContext callbackContext,
                                                                                      final AmazonWebServicesClientProxy proxy,
                                                                                      final ResourceHandlerRequest<ResourceModel> request,
                                                                                      final SyntheticsClient syntheticsClient) {

        callbackContext.setCanaryStopStarted(true);
        final GetCanaryResponse getCanaryResponse;
        // Validate if canary that is to be deleted is in the correct state, else throw error
        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder().name(model.getName()).build();
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest, syntheticsClient::getCanary);
            Canary canary = getCanaryResponse.canary();
            // Canary is in the RUNNING state. Need to STOP first
            if (canary.status().stateAsString().compareTo(CanaryStates.RUNNING.toString()) == 0) {
                StopCanaryRequest stopCanaryRequest = StopCanaryRequest.builder().name(canary.name()).build();
                proxy.injectCredentialsAndInvokeV2(stopCanaryRequest, syntheticsClient::stopCanary);
            }
        } catch (final ResourceNotFoundException resourceNotFoundException) {
            logger.log(String.format("%s [%s] is not found",
                    ResourceModel.TYPE_NAME, model.getName()));
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier().toString());
        } catch (final ConcurrentModificationException e) {
            throw new AmazonServiceException(e.getMessage());
        } catch (final ValidationException e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryStopProgress(final ResourceModel model,
                                                                                   final CallbackContext callbackContext,
                                                                                   final AmazonWebServicesClientProxy proxy,
                                                                                   final ResourceHandlerRequest<ResourceModel> request,
                                                                                   final SyntheticsClient syntheticsClient
    ) {
        boolean canaryStopStabilization = checkCanaryStopStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.incrementRetryTimes();
        if (canaryStopStabilization) {
            callbackContext.setCanaryStopStabilized(canaryStopStabilization);
        }
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

        if (canaryDeletionState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
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
        } catch (CfnInvalidRequestException cfnInvalidRequestException) {
            logger.log("ERROR: " + cfnInvalidRequestException.getMessage());
        } catch (CfnGeneralServiceException cfnGeneralServiceException) {
            logger.log("ERROR: " + cfnGeneralServiceException.getMessage());
        }
        return false;
    }

    private boolean checkCanaryStopStabilization(final ResourceModel model,
                                                 final AmazonWebServicesClientProxy proxy,
                                                 final CallbackContext callbackContext,
                                                 final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder().name(model.getName()).build();

        try {
            GetCanaryResponse getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest, syntheticsClient::getCanary);
            String canaryState = getCanaryResponse.canary().status().stateAsString();
            if (canaryState.compareTo(CanaryStates.READY.toString()) == 0
                    || canaryState.compareTo(CanaryStates.STOPPED.toString()) == 0
            ) {
                return true;
            }
        } catch (CfnInvalidRequestException cfnInvalidRequestException) {
            logger.log("ERROR: " + cfnInvalidRequestException.getMessage());
        } catch (CfnGeneralServiceException cfnGeneralServiceException) {
            logger.log("ERROR: " + cfnGeneralServiceException.getMessage());
        }
        return false;
    }
}
