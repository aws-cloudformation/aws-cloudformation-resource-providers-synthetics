package com.amazon.synthetics.canary;

import java.util.Objects;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.*;
import software.amazon.cloudformation.proxy.*;

import java.util.Map;

public class UpdateHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_SECONDS = 10;
    private static final int MAX_RETRY_TIMES = 10; // 5min * 60 / 30 = 10
    private static final String ADD_TAGS = "ADD_TAGS";
    private static final String REMOVE_TAGS = "REMOVE_TAGS";

    private Logger logger;
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
        if (!callbackContext.isCanaryUpdationStablized()) {
            return updateCanaryUpdationProgress(model, callbackContext, proxy, request, syntheticsClient);
        }

        Canary canary = getCanaryRecord(model, proxy, syntheticsClient);
        CanaryState canaryState = canary.status().state();

        if (model.getStartCanaryAfterCreation()) {
            if (canaryState != CanaryState.RUNNING) {
                if (!callbackContext.isCanaryStartStarted()) {
                    return startCanary(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if (!callbackContext.isCanaryStartStablized()) {
                    return updateCanaryStartProgress(model, callbackContext, proxy, request, syntheticsClient);
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
        } catch (ResourceNotFoundException rfne) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier().toString());
        }

        Canary canary = getCanaryResponse.canary();

        String handlerName = canary.code().handler();
        String scheduleExpression = canary.schedule().expression();
        String durationInSecs = canary.schedule().durationInSeconds()!= null ? canary.schedule().durationInSeconds().toString() : "";
        Integer timeoutInSeconds = canary.runConfig() != null ? canary.runConfig().timeoutInSeconds() : null;
        Integer memoryInMB = canary.runConfig() != null ? canary.runConfig().memoryInMB() : null;
        Integer successRetentionPeriodInDays = canary.successRetentionPeriodInDays();
        Integer failureRetentionPeriodInDays = canary.failureRetentionPeriodInDays();
        String executionRoleArn = canary.executionRoleArn();
        VpcConfigInput vpcConfigInput = null;

        if (!Objects.equals(handlerName, model.getCode().getHandler())) {
            logger.log("Updating handler");
            handlerName = model.getCode().getHandler();
        }

        if (!Objects.equals(scheduleExpression, model.getSchedule().getExpression())) {
            logger.log("Updating scheduleExpression");
            scheduleExpression = model.getSchedule().getExpression();
        }

        if (!Objects.equals(durationInSecs, model.getSchedule().getDurationInSeconds())) {
            logger.log("Updating durationInSecs");
            durationInSecs = model.getSchedule().getDurationInSeconds();
        }

        if (model.getRunConfig() != null) {
            if (!Objects.equals(timeoutInSeconds, model.getRunConfig().getTimeoutInSeconds())) {
                logger.log("Updating timeoutInSeconds");
                timeoutInSeconds = model.getRunConfig().getTimeoutInSeconds();
            }

            if (model.getRunConfig().getMemoryInMB() != null &&
                !Objects.equals(memoryInMB, model.getRunConfig().getMemoryInMB())) {
                logger.log("Updating memory");
                memoryInMB = model.getRunConfig().getMemoryInMB();
            }
        }

        if (model.getVPCConfig() != null) {
            if (model.getVPCConfig().getSubnetIds() != null &&
                !model.getVPCConfig().getSubnetIds().isEmpty() &&
                model.getVPCConfig().getSecurityGroupIds() != null &&
                !model.getVPCConfig().getSecurityGroupIds().isEmpty()) {
                logger.log("Updating vpcConfig");
                vpcConfigInput = VpcConfigInput.builder()
                    .subnetIds(model.getVPCConfig().getSubnetIds())
                    .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                    .build();
            }
        }

        if (!Objects.equals(successRetentionPeriodInDays, model.getSuccessRetentionPeriod())) {
            logger.log("Updating successRetentionPeriodInDays");
            successRetentionPeriodInDays = model.getSuccessRetentionPeriod();
        }

        if (!Objects.equals(failureRetentionPeriodInDays, model.getFailureRetentionPeriod())) {
            logger.log("Updating failureRetentionPeriodInDays");
            failureRetentionPeriodInDays = model.getFailureRetentionPeriod();
        }

        if (!Objects.equals(executionRoleArn, model.getExecutionRoleArn())) {
            logger.log("Updating executionRoleArn");
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
                .durationInSeconds(Long.valueOf(durationInSecs)).build();

        final CanaryRunConfigInput canaryRunConfigInput = CanaryRunConfigInput.builder()
                .timeoutInSeconds(timeoutInSeconds)
                .memoryInMB(memoryInMB)
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
                                                                                       final SyntheticsClient syntheticsClient) {
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
            if (canary.status().state() != CanaryState.UPDATING) {
                return true;
            }
        } catch (final ResourceNotFoundException e) {
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

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryStartProgress(final ResourceModel model,
                                                                                    final CallbackContext callbackContext,
                                                                                    final AmazonWebServicesClientProxy proxy,
                                                                                    final ResourceHandlerRequest<ResourceModel> request,
                                                                                    final SyntheticsClient syntheticsClient) {
        boolean canaryStartingState = checkCanaryStartStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryStartStablized(canaryStartingState);
        callbackContext.incrementRetryTimes();
        if (canaryStartingState) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
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
            if (canary.status().state() == CanaryState.RUNNING) {
                logger.log("Canary has successfully entered the RUNNING state");
                return true;
            }
        } catch (final ResourceNotFoundException e) {
            return false;
        }
        return false;
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
