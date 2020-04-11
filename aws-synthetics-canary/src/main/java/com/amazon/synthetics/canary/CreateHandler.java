package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final String NODE_MODULES_DIR = "/nodejs/node_modules/";
    private static final String JS_SUFFIX = ".js";
    private static final int DEFAULT_CALLBACK_DELAY_SECONDS = 5;
    private static final int CALLBACK_DELAY_SECONDS_FOR_RUNNING_STATE = 30;
    private static final int MAX_RETRY_TIMES = 10; // 5min * 60 / 30 = 10

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
        if (callbackContext.isCanaryCreationStarted() && !callbackContext.isCanaryCreationStablized()) {
            return updateCanaryCreationProgress(model, callbackContext, proxy, request, syntheticsClient);
        }

        // Canary has been successfully created. Check if it needs to be started or return SUCCESS
        if (callbackContext.isCanaryCreationStablized()) {
            if (model.getStartCanaryAfterCreation().equals(Boolean.TRUE)) {
                if (!callbackContext.isCanaryStartStarted()) {
                    return startCanary(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if (callbackContext.isCanaryStartStarted() && !callbackContext.isCanaryStartStablized()) {
                    return updateCanaryStartProgress(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if (callbackContext.isCanaryStartStablized()) {
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModel(ModelHelper.constructModel(getCanaryRecord(model,proxy,syntheticsClient), model))
                            .status(OperationStatus.SUCCESS)
                            .build();
                }
            } else {
                // Just return the canary meta data
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(ModelHelper.constructModel(getCanaryRecord(model,proxy,syntheticsClient), model))
                        .status(OperationStatus.SUCCESS)
                        .build();
            }
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.FAILED)
                .build();
    }


    private ProgressEvent<ResourceModel, CallbackContext> createCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket() != null ? model.getCode().getS3Bucket() : null)
                .s3Key(model.getCode().getS3Key() != null ? model.getCode().getS3Key() : null )
                .s3Version(model.getCode().getS3ObjectVersion() != null ? model.getCode().getS3ObjectVersion() : null )
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

        final CreateCanaryRequest createCanaryRequest = CreateCanaryRequest.builder()
                .name(model.getName())
                .executionRoleArn(model.getExecutionRoleArn())
                .schedule(canaryScheduleInput)
                .artifactS3Location(model.getArtifactS3Location())
                .runtimeVersion(model.getRuntimeVersion())
                .code(canaryCodeInput)
                .tags(buildTagInput(model))
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
                                                                                       final SyntheticsClient syntheticsClient
    ) {
        boolean canaryCreationState = checkCreateStabilization(model, proxy, callbackContext, syntheticsClient);
        callbackContext.setCanaryCreationStablized(canaryCreationState);
        callbackContext.incrementRetryTimes();
        OperationStatus operationStatus;
        Canary canary;

        if (canaryCreationState && !model.getStartCanaryAfterCreation()) {
            canary = getCanaryRecord(model,
                    proxy,
                    syntheticsClient);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .callbackContext(callbackContext)
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
            if (canary.status().stateAsString().compareTo(CanaryStates.READY.toString()) == 0) {
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
        OperationStatus operationStatus;

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

            String canaryState = getCanaryResponse.canary().status().stateAsString();
            if (canaryState.compareTo(CanaryStates.RUNNING.toString()) == 0
                    || canaryState.compareTo(CanaryStates.STOPPED.toString()) == 0) {
                logger.log(String.format("Canary has successfully entered the %s state" ,
                        CanaryStates.RUNNING.toString()));
                return true;
            }
        } catch (final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    public Map<String, String> buildTagInput(ResourceModel model) {
        Map<String, String> tagMap = new HashMap<>();
        List<Tag> tagList = new ArrayList<Tag>();
        tagList = model.getTags();
        // return null if no Tag specified.
        if (tagList == null ) return null;

        for(Tag tag: tagList) {
            tagMap.put(tag.getKey(), tag.getValue());
        }
        return tagMap;
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

