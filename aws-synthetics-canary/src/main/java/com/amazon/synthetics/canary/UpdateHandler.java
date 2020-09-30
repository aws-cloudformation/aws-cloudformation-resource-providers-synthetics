package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import java.util.Objects;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.*;
import software.amazon.cloudformation.proxy.*;

import java.util.Map;

public class UpdateHandler extends CanaryActionHandler {
    private static final int CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 10; // 5min * 60 / 30 = 10
    private static final String ADD_TAGS = "ADD_TAGS";
    private static final String REMOVE_TAGS = "REMOVE_TAGS";

    public UpdateHandler() {
        super(Action.UPDATE);
    }

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        Canary canary = getCanaryOrThrow();

        // Update Canary
        if (!context.isCanaryUpdationStarted()) {
            return updateCanary(canary);
        }

        //Update creation started. Check for stabilization.
        if (!context.isCanaryUpdationStablized()) {
            return updateCanaryUpdationProgress();
        }

        CanaryState canaryState = canary.status().state();

        if (!Strings.isNullOrEmpty(canary.status().stateReason())) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .errorCode(HandlerErrorCode.GeneralServiceException)
                .message(canary.status().stateReason())
                .status(OperationStatus.FAILED)
                .build();
        }

        if (model.getStartCanaryAfterCreation()) {
            if (canaryState != CanaryState.RUNNING) {
                if (!context.isCanaryStartStarted()) {
                    return startCanary();
                }

                // Canary has been started. Wait until it stabilizes
                if (!context.isCanaryStartStablized()) {
                    return updateCanaryStartProgress();
                }
            }

            // Canary has been started. Return SUCCESS.
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
        } else {
            if (canaryState == CanaryState.READY || canaryState == CanaryState.STOPPED) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
            }
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(model)
            .status(OperationStatus.FAILED)
            .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanary(Canary canary) {
        log("Updating canary...");

        String handlerName = canary.code().handler();
        String scheduleExpression = canary.schedule().expression();
        String durationInSecs = canary.schedule().durationInSeconds()!= null ? canary.schedule().durationInSeconds().toString() : null;
        Integer timeoutInSeconds = canary.runConfig() != null ? canary.runConfig().timeoutInSeconds() : null;
        Boolean activeTracing = canary.runConfig() != null && canary.runConfig().activeTracing() != null ? canary.runConfig().activeTracing() : false;
        Integer memoryInMB = canary.runConfig() != null ? canary.runConfig().memoryInMB() : null;
        Integer successRetentionPeriodInDays = canary.successRetentionPeriodInDays();
        Integer failureRetentionPeriodInDays = canary.failureRetentionPeriodInDays();
        String executionRoleArn = canary.executionRoleArn();
        VpcConfigInput vpcConfigInput = null;

        if (!Objects.equals(handlerName, model.getCode().getHandler())) {
            log("Updating handler");
            handlerName = model.getCode().getHandler();
        }

        if (!Objects.equals(scheduleExpression, model.getSchedule().getExpression())) {
            log("Updating scheduleExpression");
            scheduleExpression = model.getSchedule().getExpression();
        }

        if (!Objects.equals(durationInSecs, model.getSchedule().getDurationInSeconds())) {
            log("Updating durationInSecs");
            durationInSecs = model.getSchedule().getDurationInSeconds();
        }

        if (model.getRunConfig() != null) {
            if (!Objects.equals(timeoutInSeconds, model.getRunConfig().getTimeoutInSeconds())) {
                log("Updating timeoutInSeconds");
                timeoutInSeconds = model.getRunConfig().getTimeoutInSeconds();
            }

            if (model.getRunConfig().getMemoryInMB() != null &&
                !Objects.equals(memoryInMB, model.getRunConfig().getMemoryInMB())) {
                log("Updating memory");
                memoryInMB = model.getRunConfig().getMemoryInMB();
            }
            
            if (model.getRunConfig().getActiveTracing() != null && !Objects.equals(activeTracing, model.getRunConfig().getActiveTracing())) {
                    log("Updating active tracing");
                    activeTracing = Boolean.TRUE.equals(model.getRunConfig().getActiveTracing());
                }
        }

        if (model.getVPCConfig() != null) {
            if (model.getVPCConfig().getSubnetIds() != null &&
                !model.getVPCConfig().getSubnetIds().isEmpty() &&
                model.getVPCConfig().getSecurityGroupIds() != null &&
                !model.getVPCConfig().getSecurityGroupIds().isEmpty()) {
                log("Updating vpcConfig");
                vpcConfigInput = VpcConfigInput.builder()
                    .subnetIds(model.getVPCConfig().getSubnetIds())
                    .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                    .build();
            }
        }

        if (!Objects.equals(successRetentionPeriodInDays, model.getSuccessRetentionPeriod())) {
            log("Updating successRetentionPeriodInDays");
            successRetentionPeriodInDays = model.getSuccessRetentionPeriod();
        }

        if (!Objects.equals(failureRetentionPeriodInDays, model.getFailureRetentionPeriod())) {
            log("Updating failureRetentionPeriodInDays");
            failureRetentionPeriodInDays = model.getFailureRetentionPeriod();
        }

        if (!Objects.equals(executionRoleArn, model.getExecutionRoleArn())) {
            log("Updating executionRoleArn");
            executionRoleArn = model.getExecutionRoleArn();
        }

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(handlerName)
                .s3Bucket(model.getCode().getS3Bucket())
                .s3Key(model.getCode().getS3Key())
                .s3Version(model.getCode().getS3ObjectVersion())
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model.getCode()) : null)
                .build();

        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(scheduleExpression)
                .durationInSeconds(durationInSecs != null ? Long.valueOf(durationInSecs) : null).build();

        final CanaryRunConfigInput canaryRunConfigInput = CanaryRunConfigInput.builder()
                .timeoutInSeconds(timeoutInSeconds)
                .memoryInMB(memoryInMB)
                .activeTracing(activeTracing)
                .build();

        final UpdateCanaryRequest updateCanaryRequest = UpdateCanaryRequest.builder()
                .name(model.getName())
                .code(canaryCodeInput)
                .executionRoleArn(executionRoleArn)
                .runtimeVersion(model.getRuntimeVersion())
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
                Map<String, Map<String, String>> tagResourceMap = ModelHelper.updateTags(model, canary.tags());
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
        }
        catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }
        context.setCanaryUpdationStarted(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryUpdationProgress() {
        boolean canaryUpdationState = checkUpdateIntermediateStateStabilization();
        context.incrementRetryTimes();

        if (canaryUpdationState) {
            context.setCanaryUpdationStablized(true);
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkUpdateIntermediateStateStabilization() {
        if (context.getStabilizationRetryTimes() >= MAX_RETRY_TIMES) {
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        }


        Canary canary = getCanaryOrNull();
        if (canary != null && canary.status().state() != CanaryState.UPDATING) {
            return true;
        }

        return false;
    }

    private ProgressEvent<ResourceModel, CallbackContext> startCanary() {
        context.setCanaryStartStarted(true);
        final StartCanaryRequest startCanaryRequest = StartCanaryRequest.builder()
                .name(model.getName()).build();
        try {
            proxy.injectCredentialsAndInvokeV2(startCanaryRequest, syntheticsClient::startCanary);
        } catch (final InternalServerException e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME + e.getMessage());
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryStartProgress() {
        boolean canaryStartingState = checkCanaryStartStabilization();
        context.setCanaryStartStablized(canaryStartingState);
        context.incrementRetryTimes();
        if (canaryStartingState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkCanaryStartStabilization() {
        if (context.getStabilizationRetryTimes() >= MAX_RETRY_TIMES) {
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        }

        Canary canary = getCanaryOrNull();
        if (canary != null && canary.status().state() == CanaryState.RUNNING) {
            log("Canary has successfully entered the RUNNING state");
            return true;
        }

        return false;
    }
}
