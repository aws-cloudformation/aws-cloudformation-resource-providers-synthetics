package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

import java.util.Map;

public class UpdateHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 10; // 5min * 60 / 30 = 10
    private static String ADD_TAGS = "ADD_TAGS";
    private static String REMOVE_TAGS = "REMOVE_TAGS";

    Logger logger;
    private AmazonWebServicesClientProxy clientProxy;
    private ResourceHandlerRequest<ResourceModel> request;
    private SyntheticsClient syntheticsClient;
    ResourceModel modelOutput;

    private String handlerName;
    private String scheduleExpression;
    private String durationInSecs;
    private Integer timeoutInSeconds;
    private VpcConfigInput vpcConfigInput;
    private String executionRoleArn;
    private Integer successRetentionPeriodInDays;
    private Integer failureRetentionPeriodInDays;
    private Map<String, String> tags;
    private String canaryState;

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

        if (callbackContext.isCanaryUpdationStablized()) {
            Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
            String canaryState = canary.status().stateAsString();

            if (canaryState.compareTo(CanaryStates.UPDATING.toString()) != 0 &&
                    (isCanaryInReadyOrStoppedState(model, canaryState, false)
                            || isCanaryInRunningState(model, canaryState, true))) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .status(OperationStatus.SUCCESS)
                        .build();
            }

            if (model.getStartCanaryAfterCreation().equals(Boolean.TRUE)
                    && !canaryState.matches(CanaryStates.RUNNING.toString())) {
                if (!callbackContext.isCanaryStartStarted()) {
                    return startCanary(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if (callbackContext.isCanaryStartStarted() && !callbackContext.isCanaryStartStablized()) {
                    return updateCanaryStartProgress(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started.Return SUCCESS
                if (callbackContext.isCanaryStartStablized()) {
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModel(ModelHelper.constructModel(getCanaryRecord(model, proxy, syntheticsClient), model))
                            .status(OperationStatus.SUCCESS)
                            .build();
                }
            }

            if (model.getStartCanaryAfterCreation().equals(Boolean.FALSE)
                    && canaryState.matches(CanaryStates.RUNNING.toString())) {
                if (!callbackContext.isCanaryStopStarted()) {
                    return stopCanary(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary stopping has been started. Wait until it stabilizes
                if (callbackContext.isCanaryStopStarted() && !callbackContext.isCanaryStopStabilized()) {
                    return updateCanaryStopProgress(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if (callbackContext.isCanaryStopStabilized()) {
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModel(ModelHelper.constructModel(getCanaryRecord(model, proxy, syntheticsClient), model))
                            .status(OperationStatus.SUCCESS)
                            .build();
                }
            }
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.FAILED)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        logger.log("Updating canary...");
        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        final GetCanaryResponse getCanaryResponse;
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest,
                    syntheticsClient::getCanary);
            Canary canary = getCanaryResponse.canary();
            handlerName = canary.code().handler();
            scheduleExpression = canary.schedule().expression();
            durationInSecs = canary.schedule().durationInSeconds().toString();
            timeoutInSeconds = canary.runConfig().timeoutInSeconds();
            successRetentionPeriodInDays = canary.successRetentionPeriodInDays();
            failureRetentionPeriodInDays = canary.failureRetentionPeriodInDays();
            executionRoleArn = canary.executionRoleArn();
            VpcConfigOutput vpcConfig = canary.vpcConfig();
            tags = canary.tags();
            canaryState = canary.status().stateAsString();

            if (canary.code().handler().compareTo(model.getCode().getHandler()) != 0) {
                logger.log("Updating handler");
                handlerName = model.getCode().getHandler();
            }

            if (scheduleExpression.compareTo(model.getSchedule().getExpression()) != 0) {
                logger.log("Updating scheduleExpression");
                scheduleExpression = model.getSchedule().getExpression();
            }

            if (durationInSecs.compareTo(model.getSchedule().getDurationInSeconds()) != 0) {
                logger.log("Updating durationInSecs");
                durationInSecs = model.getSchedule().getDurationInSeconds();
            }

            if (timeoutInSeconds != model.getRunConfig().getTimeoutInSeconds()) {
                logger.log("Updating timeoutInSeconds");
                timeoutInSeconds = model.getRunConfig().getTimeoutInSeconds();
            }

            if (model.getVPCConfig() != null && !vpcConfig.equals(model.getVPCConfig())) {
                logger.log("Updating vpcConfig");
                vpcConfigInput = VpcConfigInput.builder()
                        .subnetIds(model.getVPCConfig().getSubnetIds())
                        .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                        .build();
            }

            if (successRetentionPeriodInDays != model.getSuccessRetentionPeriod()) {
                logger.log("Updating successRetentionPeriodInDays");
                successRetentionPeriodInDays = model.getSuccessRetentionPeriod();
            }

            if (failureRetentionPeriodInDays != model.getFailureRetentionPeriod()) {
                logger.log("Updating failureRetentionPeriodInDays");
                failureRetentionPeriodInDays = model.getFailureRetentionPeriod();
            }

            if (executionRoleArn.compareTo(model.getExecutionRoleArn()) != 0) {
                logger.log("Updating executionRoleArn");
                executionRoleArn = model.getExecutionRoleArn();
            }

        } catch (ResourceNotFoundException rfne) {
            throw new CfnInvalidRequestException(rfne.getMessage());
        }

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(handlerName)
                .s3Bucket(model.getCode().getS3Bucket() != null ? model.getCode().getS3Bucket() : null)
                .s3Key(model.getCode().getS3Key() != null ? model.getCode().getS3Key() : null)
                .s3Version(model.getCode().getS3ObjectVersion() != null ? model.getCode().getS3ObjectVersion() : null)
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model.getCode()) : null)
                .build();

        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(scheduleExpression)
                .durationInSeconds(Long.valueOf(durationInSecs)).build();

        final CanaryRunConfigInput canaryRunConfigInput = CanaryRunConfigInput.builder()
                .timeoutInSeconds(timeoutInSeconds)
                .build();

        final UpdateCanaryRequest updateCanaryRequest = UpdateCanaryRequest.builder()
                .name(model.getName())
                .code(canaryCodeInput)
                .executionRoleArn(executionRoleArn)
                .schedule(canaryScheduleInput)
                .runConfig(canaryRunConfigInput)
                .successRetentionPeriodInDays(successRetentionPeriodInDays)
                .failureRetentionPeriodInDays(failureRetentionPeriodInDays)
                .vpcConfig(vpcConfigInput)
                .build();

        try {
            proxy.injectCredentialsAndInvokeV2(updateCanaryRequest, syntheticsClient::updateCanary);
            // if tags need to be updated then we need to call TagResourceRequest
            if (model.getTags() != null) {
                Map<String, Map<String, String>> tagResourceMap = ModelHelper.updateTags(model, tags);
                if (!tagResourceMap.get(ADD_TAGS).isEmpty()) {
                    TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                            .resourceArn(ModelHelper.buildCanaryArn(request, model.getName()))
                            .tags(tagResourceMap.get(ADD_TAGS))
                            .build();
                    proxy.injectCredentialsAndInvokeV2(tagResourceRequest, syntheticsClient::tagResource);
                }

                if (!tagResourceMap.get(REMOVE_TAGS).isEmpty()) {
                    UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
                            .resourceArn(ModelHelper.buildCanaryArn(request, model.getName()))
                            .tagKeys(tagResourceMap.get(REMOVE_TAGS).keySet())
                            .build();
                    proxy.injectCredentialsAndInvokeV2(untagResourceRequest, syntheticsClient::untagResource);
                }
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
        boolean canaryUpdationState = checkUpdateIntermediateStateStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.incrementRetryTimes();

        if (canaryUpdationState) {
            callbackContext.setCanaryUpdationStablized(canaryUpdationState);
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkUpdateIntermediateStateStabilization(final ResourceModel model,
                                                              final AmazonWebServicesClientProxy proxy,
                                                              final CallbackContext callbackContext,
                                                              final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        try {
            Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
            String canaryState = canary.status().stateAsString();

            if (canaryState.compareTo(CanaryStates.UPDATING.toString()) != 0) {
                modelOutput = ModelHelper.constructModel(canary, model);
                return true;
            } /*else if (canaryState.compareTo(CanaryStates.UPDATING.toString()) != 0 &&
                    (!isCanaryInReadyOrStoppedState(model, canaryState, false)
                            || !isCanaryInRunningState(model, canaryState, true))) {

            }*/
        } catch (
                final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    private ProgressEvent<ResourceModel, CallbackContext> startCanary(final ResourceModel model,
                                                                      final CallbackContext callbackContext,
                                                                      final AmazonWebServicesClientProxy proxy,
                                                                      final ResourceHandlerRequest<ResourceModel> request,
                                                                      final SyntheticsClient syntheticsClient) {
        callbackContext.setCanaryStartStarted(true);
        final StartCanaryRequest startCanaryRequest = StartCanaryRequest.builder()
                .name(model.getName()).build();
        try {
            proxy.injectCredentialsAndInvokeV2(startCanaryRequest, syntheticsClient::startCanary);
        } catch (final InternalServerException e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME + e.getMessage());
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> stopCanary(final ResourceModel model,
                                                                     final CallbackContext callbackContext,
                                                                     final AmazonWebServicesClientProxy proxy,
                                                                     final ResourceHandlerRequest<ResourceModel> request,
                                                                     final SyntheticsClient syntheticsClient) {
        callbackContext.setCanaryStopStarted(true);
        final StopCanaryRequest stopCanaryRequest = StopCanaryRequest.builder()
                .name(model.getName()).build();
        try {
            proxy.injectCredentialsAndInvokeV2(stopCanaryRequest, syntheticsClient::stopCanary);
        } catch (final InternalServerException e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME + e.getMessage());
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryStartProgress(final ResourceModel model,
                                                                                    final CallbackContext callbackContext,
                                                                                    final AmazonWebServicesClientProxy proxy,
                                                                                    final ResourceHandlerRequest<ResourceModel> request,
                                                                                    final SyntheticsClient syntheticsClient) {
        boolean canaryStartingState = checkCanaryStartStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryStartStablized(canaryStartingState);
        callbackContext.incrementRetryTimes();
        Canary canary = getCanaryRecord(model,
                proxy,
                syntheticsClient);
        if (canaryStartingState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
                    .resourceModel(ModelHelper.constructModel(canary, model))
                    .status(OperationStatus.SUCCESS)
                    .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(ModelHelper.constructModel(canary, model))
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryStopProgress(final ResourceModel model,
                                                                                   final CallbackContext callbackContext,
                                                                                   final AmazonWebServicesClientProxy proxy,
                                                                                   final ResourceHandlerRequest<ResourceModel> request,
                                                                                   final SyntheticsClient syntheticsClient) {
        boolean canaryStoppingState = checkCanaryStopStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryStopStabilized(canaryStoppingState);
        callbackContext.incrementRetryTimes();
        Canary canary = getCanaryRecord(model,
                proxy,
                syntheticsClient);
        if (canaryStoppingState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
                    .resourceModel(ModelHelper.constructModel(canary, model))
                    .status(OperationStatus.SUCCESS)
                    .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(ModelHelper.constructModel(canary, model))
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkCanaryStartStabilization(final ResourceModel model,
                                                  final AmazonWebServicesClientProxy proxy,
                                                  final CallbackContext callbackContext,
                                                  final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        try {
            Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
            String canaryState = canary.status().stateAsString();
            if (canaryState.compareTo(CanaryStates.RUNNING.toString()) == 0) {
                logger.log(String.format("Canary has successfully entered the %s state",
                        CanaryStates.RUNNING.toString()));
                return true;
            }
        } catch (final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    private boolean checkCanaryStopStabilization(final ResourceModel model,
                                                 final AmazonWebServicesClientProxy proxy,
                                                 final CallbackContext callbackContext,
                                                 final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());

        try {
            Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
            String canaryState = canary.status().stateAsString();
            if (canaryState.compareTo(CanaryStates.STOPPED.toString()) == 0) {
                logger.log(String.format("Canary has successfully entered the %s state",
                        CanaryStates.STOPPED.toString()));
                return true;
            }
        } catch (final ResourceNotFoundException e) {
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
