package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

public class UpdateHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 10; // 5min * 60 / 30 = 10

    Logger logger;
    private AmazonWebServicesClientProxy clientProxy;
    private ResourceHandlerRequest<ResourceModel> request;
    private SyntheticsClient syntheticsClient;
    ResourceModel modelOutput;

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
        return updateCanaryAndUpdateProgress(model, currentContext, proxy, request, syntheticsClient);
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryAndUpdateProgress(final ResourceModel model,
                                                                                        final CallbackContext callbackContext,
                                                                                        final AmazonWebServicesClientProxy proxy,
                                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                                        final SyntheticsClient syntheticsClient) {
        // Update Canary
        if (!callbackContext.isCanaryUpdationStarted()) {
            return updateCanary(model, callbackContext, proxy, request, syntheticsClient);
        }

        //Update creation started. Check for stabilization.
        if (callbackContext.isCanaryUpdationStarted() && !callbackContext.isCanaryUpdationStablized()) {
            return updateCanaryUpdationProgress(model, callbackContext, proxy, request, syntheticsClient);
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket() != null ? model.getCode().getS3Bucket() : null)
                .s3Key(model.getCode().getS3Key() != null ? model.getCode().getS3Key() : null)
                .s3Version(model.getCode().getS3ObjectVersion() != null ? model.getCode().getS3ObjectVersion() : null)
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model.getCode()) : null)
                .build();

        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(model.getSchedule().getExpression())
                .durationInSeconds(Long.valueOf((model.getSchedule().getDurationInSeconds()))).build();

        final CanaryRunConfigInput canaryRunConfigInput = CanaryRunConfigInput.builder()
                .timeoutInSeconds(model.getRunConfig().getTimeoutInSeconds())
                .build();

        // VPC Config optional
        VpcConfigInput vpcConfigInput = null;

        if (model.getVPCConfig() != null) {
            vpcConfigInput = VpcConfigInput.builder()
                    .subnetIds(model.getVPCConfig().getSubnetIds())
                    .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                    .build();
        }

        final UpdateCanaryRequest updateCanaryRequest = UpdateCanaryRequest.builder()
                .name(model.getName())
                .code(canaryCodeInput)
                .executionRoleArn(model.getExecutionRoleArn())
                .runtimeVersion(model.getRuntimeVersion())
                .schedule(canaryScheduleInput)
                .runConfig(canaryRunConfigInput)
                .successRetentionPeriodInDays(model.getSuccessRetentionPeriod())
                .failureRetentionPeriodInDays(model.getFailureRetentionPeriod())
                .vpcConfig(vpcConfigInput)
                .build();

        final Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
        final String canaryState = canary.status().stateAsString();

        try {
            proxy.injectCredentialsAndInvokeV2(updateCanaryRequest, syntheticsClient::updateCanary);
            // if tags need to be updated then we need to call TagResourceRequest
            if (model.getTags() != null) {
                TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                        .resourceArn(ModelHelper.buildCanaryArn(request, model.getName()))
                        .tags(ModelHelper.buildTagInputMap(model))
                        .build();
                proxy.injectCredentialsAndInvokeV2(tagResourceRequest, syntheticsClient::tagResource);
            }

            if (isCanaryInReadyOrStoppedState(model, canaryState, true)) {
                StartCanaryRequest startCanaryRequest = StartCanaryRequest.builder()
                        .name(model.getName()).build();

                logger.log("Starting canary....: State : " + canary.status().stateAsString());
                proxy.injectCredentialsAndInvokeV2(startCanaryRequest, syntheticsClient::startCanary);
            }
            
            if (isCanaryInRunningState(model, canaryState, false)) {
                logger.log("Stopping canary....: " + model.getName() + " in State : " + canary.status().stateAsString() + "\n");
                StopCanaryRequest stopCanaryRequest = StopCanaryRequest.builder()
                        .name(model.getName()).build();

                proxy.injectCredentialsAndInvokeV2(stopCanaryRequest, syntheticsClient::stopCanary);
            }

        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }

        callbackContext.setCanaryUpdationStarted(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryUpdationProgress(final ResourceModel model,
                                                                                       final CallbackContext callbackContext,
                                                                                       final AmazonWebServicesClientProxy proxy,
                                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                                       final SyntheticsClient syntheticsClient
    ) {
        boolean canaryUpdationState = checkUpdateStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryUpdationStablized(canaryUpdationState);
        callbackContext.incrementRetryTimes();

        if (canaryUpdationState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
                    .resourceModel(modelOutput)
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

    private boolean checkUpdateStabilization(final ResourceModel model,
                                             final AmazonWebServicesClientProxy proxy,
                                             final CallbackContext callbackContext,
                                             final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        try {
            Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
            String canaryState = canary.status().stateAsString();
            if (canaryState.compareTo(CanaryStates.UPDATING.toString()) != 0 &&
                    (isCanaryInReadyOrStoppedState(model, canaryState, false)
                            || isCanaryInRunningState(model, canaryState, true))) {

                modelOutput = ModelHelper.constructModel(canary, model);
                return true;
            }
        } catch (
                final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    private boolean isCanaryInReadyOrStoppedState(ResourceModel model, String canaryState, boolean requiredEndState) {
        /**
         * If stack is updated setting getStartCanaryAfterCreation to false
         * AND canary returns to READY or STOPPED state, return true.
         */
        return model.getStartCanaryAfterCreation() == requiredEndState
                && (canaryState.compareTo(CanaryStates.READY.toString()) == 0
                || canaryState.compareTo(CanaryStates.STOPPED.toString()) == 0);
    }

    private boolean isCanaryInRunningState(ResourceModel model, String canaryState, boolean requiredEndState) {
        /**
         * If stack is updated setting getStartCanaryAfterCreation to true
         * AND canary is set to RUNNING state, return true.
         */
        return model.getStartCanaryAfterCreation() == requiredEndState
                && (canaryState.compareTo(CanaryStates.RUNNING.toString()) == 0);
    }


    // Get the canary metadata
    private Canary getCanaryRecord(final ResourceModel model,
                                   final AmazonWebServicesClientProxy proxy,
                                   final SyntheticsClient syntheticsClient) {
        Canary canary;
        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        final GetCanaryResponse getCanaryResponse;
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest,
                    syntheticsClient::getCanary);
            canary = getCanaryResponse.canary();
        } catch (final ResourceNotFoundException e) {
            throw new CfnInternalFailureException();
        }
        return canary;
    }
}
