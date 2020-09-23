package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeInput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleInput;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigInput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigInput;
import software.amazon.awssdk.services.synthetics.model.CreateCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.services.synthetics.model.StartCanaryRequest;

import software.amazon.cloudformation.exceptions.*;
import software.amazon.cloudformation.proxy.*;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final int DEFAULT_CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 30;
    private static final int DEFAULT_MEMORY_IN_MB = 960;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        final CallbackContext currentContext = callbackContext == null ?
            CallbackContext.builder().build() :
            callbackContext;
        SyntheticsClient syntheticsClient = ClientBuilder.getClient();

        logger.log(String.format("[CREATE] Create handler called for canary %s. RetryKey = %s and RemainingRetryCount = %d.",
            model.getName(), currentContext.getRetryKey(), currentContext.getRemainingRetryCount()));

        if (!currentContext.isCanaryCreationStarted()) {
            // Creation has yet to begin

            logger.log(String.format("[CREATE] Creating canary %s.", model.getName()));
            currentContext.setCanaryCreationStarted(true);

            return createCanary(model, currentContext, proxy, request, syntheticsClient);
        }

        Canary canary = CanaryHelper.getCanaryOrThrow(proxy, syntheticsClient, model);
        if (canary.status().state() == CanaryState.CREATING) {
            currentContext.throwIfRetryLimitExceeded(MAX_RETRY_TIMES, "CREATING", model);
            logger.log(String.format("[CREATE] Canary %s is in state CREATING.", canary.name()));
            return progressWithMessage("Creating canary", currentContext, model);
        } else if (canary.status().state() == CanaryState.ERROR) {
            logger.log(String.format("[CREATE] Canary %s is in state ERROR. %s", canary.name(), canary.status().stateReason()));
            return ProgressEvent.failed(
                model,
                currentContext,
                HandlerErrorCode.GeneralServiceException,
                canary.status().stateReason());
        } else if (canary.status().state() == CanaryState.READY) {
            currentContext.throwIfRetryLimitExceeded(MAX_RETRY_TIMES, "READY", model);
            logger.log(String.format("[CREATE] Canary %s is in state READY.", canary.name()));
            if (model.getStartCanaryAfterCreation()) {
                logger.log(String.format("[CREATE] Starting canary %s.", canary.name()));
                proxy.injectCredentialsAndInvokeV2(
                    StartCanaryRequest.builder()
                        .name(canary.name())
                        .build(),
                    syntheticsClient::startCanary);
                return progressWithMessage("Starting canary", currentContext, model);
            } else {
                return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
            }
        } else if (canary.status().state() == CanaryState.STARTING) {
            currentContext.throwIfRetryLimitExceeded(MAX_RETRY_TIMES, "STARTING", model);
            logger.log(String.format("[CREATE] Canary %s is in state STARTING.", canary.name()));
            return progressWithMessage("Starting canary", currentContext, model);
        } else {
            return ProgressEvent.defaultSuccessHandler(ModelHelper.constructModel(canary, model));
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket())
                .s3Key(model.getCode().getS3Key())
                .s3Version(model.getCode().getS3ObjectVersion())
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model.getCode()) : null)
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

        callbackContext.setCanaryCreationStarted(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(DEFAULT_CALLBACK_DELAY_SECONDS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> progressWithMessage(String message, CallbackContext context, ResourceModel model) {
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .message(message)
            .callbackContext(context)
            .resourceModel(model)
            .status(OperationStatus.IN_PROGRESS)
            .callbackDelaySeconds(DEFAULT_CALLBACK_DELAY_SECONDS)
            .build();
    }
}

