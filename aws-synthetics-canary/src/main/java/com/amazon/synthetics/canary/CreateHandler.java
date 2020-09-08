package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeInput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleInput;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigInput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigInput;
import software.amazon.awssdk.services.synthetics.model.CreateCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.services.synthetics.model.StartCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.InternalServerException;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryResponse;

import software.amazon.cloudformation.exceptions.*;
import software.amazon.cloudformation.proxy.*;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final int DEFAULT_CALLBACK_DELAY_SECONDS = 10;
    private static final int CALLBACK_DELAY_SECONDS_FOR_RUNNING_STATE = 30;
    private static final int MAX_RETRY_TIMES = 30;
    private static final int DEFAULT_MEMORY_IN_MB = 960;

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
        return createCanaryAndUpdateProgress(model, currentContext, proxy, request, syntheticsClient);
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCanaryAndUpdateProgress(final ResourceModel model,
                                                                                        final CallbackContext callbackContext,
                                                                                        final AmazonWebServicesClientProxy proxy,
                                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                                        final SyntheticsClient syntheticsClient) {
        // Create Canary
        if (!callbackContext.isCanaryCreationStarted()) {
            return createCanary(model, callbackContext, proxy, request, syntheticsClient);
        }

        //Canary creation started. Check for stabilization.
        if (!callbackContext.isCanaryCreationStablized()) {
            return updateCanaryCreationProgress(model, callbackContext, proxy, request, syntheticsClient);
        }

        // Canary has been successfully created. Check if it needs to be started or return SUCCESS
        if (model.getStartCanaryAfterCreation()) {
            if (!callbackContext.isCanaryStartStarted()) {
                return startCanary(model, callbackContext, proxy, request, syntheticsClient);
            }

            // Canary has been started. Wait until it stabilizes
            if (!callbackContext.isCanaryStartStablized()) {
                return updateCanaryStartProgress(model, callbackContext, proxy, request, syntheticsClient);
            }

            // Canary has been started. Wait until it stabilizes
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(ModelHelper.constructModel(getCanaryRecord(model,proxy,syntheticsClient), model))
                    .status(OperationStatus.SUCCESS)
                    .build();
        } else {
            // Just return the canary meta data
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(ModelHelper.constructModel(getCanaryRecord(model,proxy,syntheticsClient), model))
                    .status(OperationStatus.SUCCESS)
                    .build();
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

    private ProgressEvent<ResourceModel, CallbackContext> updateCanaryCreationProgress(final ResourceModel model,
                                                                                       final CallbackContext callbackContext,
                                                                                       final AmazonWebServicesClientProxy proxy,
                                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                                       final SyntheticsClient syntheticsClient) {
        boolean canaryCreationState = checkCreateStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryCreationStablized(canaryCreationState);
        callbackContext.incrementRetryTimes();
        Canary canary;

        if (canaryCreationState && !model.getStartCanaryAfterCreation()) {
            canary = getCanaryRecord(model,
                    proxy,
                    syntheticsClient);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(ModelHelper.constructModel(canary, model))
                    .status(OperationStatus.SUCCESS)
                    .build();
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(DEFAULT_CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkCreateStabilization(final ResourceModel model,
                                             final AmazonWebServicesClientProxy proxy,
                                             final CallbackContext callbackContext,
                                             final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        try {
            Canary canary = getCanaryRecord(model,
                    proxy,
                    syntheticsClient);
            if (canary.status().state() == CanaryState.READY) {
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
                .callbackDelaySeconds(DEFAULT_CALLBACK_DELAY_SECONDS)
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
                    .resourceModel(ModelHelper.constructModel(canary, model))
                    .status(OperationStatus.SUCCESS)
                    .build();
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(OperationStatus.IN_PROGRESS)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS_FOR_RUNNING_STATE)
                .build();
    }

    private boolean checkCanaryStartStabilization(final ResourceModel model,
                                                  final AmazonWebServicesClientProxy proxy,
                                                  final CallbackContext callbackContext,
                                                  final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        final GetCanaryRequest getCanaryRequest = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        final GetCanaryResponse getCanaryResponse;
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest,
                    syntheticsClient::getCanary);

            CanaryState canaryState = getCanaryResponse.canary().status().state();
            if (canaryState == CanaryState.RUNNING || canaryState == CanaryState.STOPPED) {
                logger.log(String.format("Canary has successfully entered the %s state" , canaryState));
                return true;
            }
        } catch (final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    // Get the canary metadata that got created.
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

