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
        if (!context.isCanaryUpdateStarted()) {
            if (canary.status().state() == CanaryState.CREATING) {
                String message = "Canary is in state CREATING and cannot be updated.";
                log(message);
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .message(message)
                    .resourceModel(model)
                    .errorCode(HandlerErrorCode.ResourceConflict)
                    .status(OperationStatus.FAILED)
                    .build();
            } else if (canary.status().state() == CanaryState.UPDATING) {
                String message = "Canary is already updating.";
                log(message);
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .message(message)
                    .resourceModel(model)
                    .errorCode(HandlerErrorCode.ResourceConflict)
                    .status(OperationStatus.FAILED)
                    .build();
            } else if (canary.status().state() == CanaryState.STARTING) {
                String message = "Canary is in state STARTING. It must finish starting before it can be updated.";
                return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "STARTING");
            } else if (canary.status().state() == CanaryState.STOPPING) {
                String message = "Canary is in state STOPPING. It must finish stopping before it can be updated.";
                return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "STOPPING");
            } else {
                context.setInitialCanaryState(canary.status().state());
                context.setCanaryUpdateStarted(true);
                return updateCanary(canary);
            }
        }

        if (canary.status().state() == CanaryState.UPDATING) {
            return waitingForCanaryStateTransition("Update in progress", MAX_RETRY_TIMES, "UPDATING");
        } else if (canary.status().state() == CanaryState.ERROR) {
            log(String.format("Canary is in state ERROR. %s", canary.status().stateReason()));
            return ProgressEvent.failed(
                model,
                context,
                HandlerErrorCode.GeneralServiceException,
                canary.status().stateReason());
        } else if (canary.status().state() == CanaryState.READY || canary.status().state() == CanaryState.STOPPED) {
            log(String.format("Canary is in state %s.", canary.status().stateAsString()));

            if (model.getStartCanaryAfterCreation()) {
                // There is a race condition here. We will get an exception if someone calls
                // DeleteCanary, StartCanary, or UpdateCanary before we call StartCanary.

                proxy.injectCredentialsAndInvokeV2(
                    StartCanaryRequest.builder()
                        .name(canary.name())
                        .build(),
                    syntheticsClient::startCanary);

                return waitingForCanaryStateTransition("Starting canary", MAX_RETRY_TIMES, "READY");
            } else {
                return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
            }
        } else if (canary.status().state() == CanaryState.STARTING) {
            // If the customer calls StartCanary before we handle the canary in READY or
            // STOPPED state, then we can end up here even when StartCanaryAfterCreation is false.

            if (model.getStartCanaryAfterCreation()) {
                return waitingForCanaryStateTransition(
                    "Starting canary",
                    "Canary is in state STARTING.",
                    MAX_RETRY_TIMES,
                    "STARTING");
            } else {
                log("Canary is in STARTING state even though StartCanaryAfterCreation was false.");
                return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
            }
        } else if (canary.status().state() == CanaryState.RUNNING) {
            log("Canary is in state RUNNING.");

            if (context.getInitialCanaryState() == CanaryState.RUNNING) {
                // If the canary was initially in state RUNNING and there was an error
                // during provisioning, then it will be set to RUNNING again and the message
                // will be in the StateReason field.
                if (!Strings.isNullOrEmpty(canary.status().stateReason())) {
                    log(String.format("Update failed: %s", canary.status().stateReason()));
                    return ProgressEvent.failed(
                        model,
                        context,
                        HandlerErrorCode.GeneralServiceException,
                        canary.status().stateReason());
                }

                // If the canary was initially in state RUNNING and StartCanaryAfterCreation is
                // false, we should stop the canary.
                if (!model.getStartCanaryAfterCreation()) {
                    // There is a race condition here. We will get an exception if someone calls
                    // DeleteCanary, StopCanary, or UpdateCanary before we call StopCanary.
                    proxy.injectCredentialsAndInvokeV2(
                        StopCanaryRequest.builder()
                            .name(canary.name())
                            .build(),
                        syntheticsClient::stopCanary);

                    return waitingForCanaryStateTransition("Stopping canary", MAX_RETRY_TIMES, "RUNNING");
                }
            }

            return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
        } else if (canary.status().state() == CanaryState.STOPPING) {
            return waitingForCanaryStateTransition("Stopping canary", MAX_RETRY_TIMES, "STOPPING");
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(model)
            .status(OperationStatus.FAILED)
            .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateCanary(Canary canary) {
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
            throw new CfnInvalidRequestException(e);
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e);
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }
}
