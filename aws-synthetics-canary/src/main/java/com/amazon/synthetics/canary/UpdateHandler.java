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
        //syntheticsClient = ClientBuilder.getClient();
        syntheticsClient=ClientBuilder.getClient("us-west-2","https://9za3kue24h.execute-api.us-west-2.amazonaws.com/test");

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
                .executionRoleArn(model.getExecutionRoleArn())
                .schedule(canaryScheduleInput)
                .runConfig(canaryRunConfigInput)
                .successRetentionPeriodInDays(model.getSuccessRetentionPeriod())
                .failureRetentionPeriodInDays(model.getFailureRetentionPeriod())
                .vpcConfig(vpcConfigInput)
                .build();

        try {
            proxy.injectCredentialsAndInvokeV2(updateCanaryRequest, syntheticsClient::updateCanary);
            // if tags need to be updated then we need to call TagResourceRequest
            if (model.getTags() != null) {
                Map<String, Map<String, String>> tagResourceMap = ModelHelper.updateTags(model, tags);
                logger.log("tagResourceMap: " + tagResourceMap);
                if (tagResourceMap.get("ADD_TAGS") != null ) {
                    logger.log("tagResourceMap: in ADD" + tagResourceMap);
                    TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                            .resourceArn(ModelHelper.buildCanaryArn(request, model.getName()))
                            .tags(ModelHelper.updateTags(model, tags).get("ADD_TAGS"))
                            .build();
                    proxy.injectCredentialsAndInvokeV2(tagResourceRequest, syntheticsClient::tagResource);
                }

                if (tagResourceMap.get("REMOVE_TAGS") != null) {
                    logger.log("tagResourceMap: in REMOVE" + tagResourceMap);
                    UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
                            .resourceArn(ModelHelper.buildCanaryArn(request, model.getName()))
                            .tagKeys(ModelHelper.updateTags(model, tags).get("REMOVE_TAGS").keySet())
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
