package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeInput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleInput;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigInput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigInput;
import software.amazon.awssdk.services.synthetics.model.CreateCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.services.synthetics.model.StartCanaryRequest;

import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.*;
import software.amazon.cloudformation.proxy.*;

public class CreateHandler extends CanaryActionHandler {
    private static final int DEFAULT_CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 120;
    private static final int DEFAULT_MEMORY_IN_MB = 960;

    public CreateHandler() {
        super(Action.CREATE);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        if (!context.isCanaryCreateStarted()) {
            // Creation has yet to begin

            log("Creating canary.");
            context.setCanaryCreateStarted(true);

            return createCanary();
        }

        Canary canary = getCanaryOrThrow();
        if (canary.status().state() == CanaryState.CREATING) {
            return waitingForCanaryStateTransition(
                "Creating canary",
                "Canary is in state CREATING.",
                MAX_RETRY_TIMES,
                "CREATING");
        } else if (canary.status().state() == CanaryState.ERROR) {
            log(String.format("Canary is in state ERROR. %s", canary.status().stateReason()));
            return ProgressEvent.failed(
                model,
                context,
                HandlerErrorCode.GeneralServiceException,
                canary.status().stateReason());
        } else if (canary.status().state() == CanaryState.READY) {
            return handleCanaryInStateReady(canary);
        } else if (canary.status().state() == CanaryState.STARTING) {
            return handleCanaryInStateStarting(canary);
        } else {
            return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleCanaryInStateReady(Canary canary) {
        log("Canary is in state READY.");
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
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleCanaryInStateStarting(Canary canary) {
        // If the customer calls StartCanary before we handle the canary in READY state,
        // then we can end up here even when StartCanaryAfterCreation is false.

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
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCanary() {
        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket())
                .s3Key(model.getCode().getS3Key())
                .s3Version(model.getCode().getS3ObjectVersion())
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model) : null)
                .build();

        Long durationInSeconds = !Strings.isNullOrEmpty(model.getSchedule().getDurationInSeconds()) ?
                Long.valueOf(model.getSchedule().getDurationInSeconds()) : null;

        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(model.getSchedule().getExpression())
                .durationInSeconds(durationInSeconds)
                .build();

        int memoryInMb = DEFAULT_MEMORY_IN_MB;
        CanaryRunConfigInput canaryRunConfigInput = null;
        if (model.getRunConfig() != null) {
            // memoryInMb is optional input. Default value if no value is supplied
            memoryInMb = model.getRunConfig().getMemoryInMB() != null ?
                    model.getRunConfig().getMemoryInMB() : DEFAULT_MEMORY_IN_MB;

            canaryRunConfigInput = CanaryRunConfigInput.builder()
                .timeoutInSeconds(model.getRunConfig().getTimeoutInSeconds())
                .memoryInMB(memoryInMb)
                .activeTracing(Boolean.TRUE.equals(model.getRunConfig().getActiveTracing()))
                .environmentVariables(model.getRunConfig().getEnvironmentVariables())
                .build();
        }

        // VPC Config optional
        VpcConfigInput vpcConfigInput = null;

        if (model.getVPCConfig() != null) {
            vpcConfigInput = VpcConfigInput.builder()
                    .subnetIds(model.getVPCConfig().getSubnetIds())
                    .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                    .build();
        }

        final CreateCanaryRequest createCanaryRequest = CreateCanaryRequest.builder()
                .name(model.getName())
                .executionRoleArn(model.getExecutionRoleArn())
                .schedule(canaryScheduleInput)
                .artifactS3Location(model.getArtifactS3Location())
                .runtimeVersion(model.getRuntimeVersion())
                .code(canaryCodeInput)
                .tags(ModelHelper.buildTagInputMap(model))
                .vpcConfig(vpcConfigInput)
                .failureRetentionPeriodInDays(model.getFailureRetentionPeriod())
                .successRetentionPeriodInDays(model.getSuccessRetentionPeriod())
                .runConfig(canaryRunConfigInput)
                .build();
        try {
            proxy.injectCredentialsAndInvokeV2(createCanaryRequest, syntheticsClient::createCanary);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e.getMessage());
        }

        context.setCanaryCreateStarted(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(context)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(DEFAULT_CALLBACK_DELAY_SECONDS)
                .build();
    }
}

