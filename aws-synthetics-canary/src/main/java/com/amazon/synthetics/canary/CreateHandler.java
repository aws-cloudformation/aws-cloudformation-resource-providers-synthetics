package com.amazon.synthetics.canary;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final String NODE_MODULES_DIR = "/nodejs/node_modules/";
    private static final String JS_SUFFIX = ".js";
    private static final int CALLBACK_DELAY_SECONDS = 30;
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
        syntheticsClient = ClientBuilder.getClient("us-west-2","https://9za3kue24h.execute-api.us-west-2.amazonaws.com/test");

        // This Lambda will continually be re-invoked with the current state of the instance, finally succeeding when state stabilizes.
        return createCanaryAndUpdateProgress(model, currentContext, proxy, request, syntheticsClient);
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCanaryAndUpdateProgress(final ResourceModel model,
                                                                                        final CallbackContext callbackContext,
                                                                                        final AmazonWebServicesClientProxy proxy,
                                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                                        final SyntheticsClient syntheticsClient) {
        // Create Canary
        if(!callbackContext.isCanaryCreationStarted()) {
            return createCanary( model, callbackContext, proxy, request, syntheticsClient);
        }

        //Canary creation started. Check for stabilization.
        if(callbackContext.isCanaryCreationStarted() && !callbackContext.isCanaryCreationStablized()) {
            return updateCanaryCreationProgress( model, callbackContext, proxy, request, syntheticsClient);
        }

        // Canary has been successfully created. Check if it needs to be started or return SUCCESS
        if(callbackContext.isCanaryCreationStablized()) {
            if(model.getStartCanaryAfterCreation().equals(Boolean.TRUE)){
                if (!callbackContext.isCanaryStartStarted()) {
                    return startCanary(model, callbackContext, proxy, request, syntheticsClient);
                }

                // Canary has been started. Wait until it stabilizes
                if(callbackContext.isCanaryStartStarted() && !callbackContext.isCanaryStartStablized()) {
                    return updateCanaryStartProgress( model, callbackContext, proxy, request, syntheticsClient);
                }
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


    private ProgressEvent<ResourceModel, CallbackContext> createCanary(final ResourceModel model,
                                                                       final CallbackContext callbackContext,
                                                                       final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final SyntheticsClient syntheticsClient) {

        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket())
                .s3Key(model.getCode().getS3Bucket())
                .s3Version(model.getCode().getS3ObjectVersion())
                .zipFile(compressRawScript(model.getCode())).build();

        logger.log("\n script: " + model.getCode().getScript());

        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(model.getSchedule().getExpression())
                .durationInSeconds(Long.valueOf((model.getSchedule().getDurationInSeconds()))).build();

        final CreateCanaryRequest createCanaryRequest = CreateCanaryRequest.builder()
                .name(model.getName())
                .executionRoleArn(model.getExecutionIAMRoleArn())
                .schedule(canaryScheduleInput)
                .artifactLocation(model.getArtifactLocation())
                .runtimeVersion(model.getRuntimeVersion())
                .code(canaryCodeInput)
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
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
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

        if(canaryCreationState && !model.getStartCanaryAfterCreation()) {
            operationStatus = OperationStatus.SUCCESS;
        } else {
            operationStatus = OperationStatus.IN_PROGRESS;
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(operationStatus)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkCreateStabilization(final ResourceModel model,
                                             final AmazonWebServicesClientProxy proxy,
                                             final CallbackContext callbackContext,
                                             final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        final GetCanaryRequest getCanaryRequest  = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        final GetCanaryResponse getCanaryResponse;
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest,
                    syntheticsClient::getCanary);
            String canaryState = getCanaryResponse.canary().status().stateAsString();
            if (canaryState.compareTo(CanaryStates.READY.toString()) == 0) {
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
        final StartCanaryRequest startCanaryRequest = StartCanaryRequest.builder()
                .name(model.getName()).build();
        try {
            proxy.injectCredentialsAndInvokeV2(startCanaryRequest, syntheticsClient::startCanary);
        } catch (final InternalServerException e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME + e.getMessage());
        }
        callbackContext.setCanaryStartStarted(true);
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
        OperationStatus operationStatus;

        if(canaryStartingState) {
            operationStatus = OperationStatus.SUCCESS;
        } else {
            operationStatus = OperationStatus.IN_PROGRESS;
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(callbackContext)
                .resourceModel(model)
                .status(operationStatus)
                .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                .build();
    }

    private boolean checkCanaryStartStabilization(final ResourceModel model,
                                             final AmazonWebServicesClientProxy proxy,
                                             final CallbackContext callbackContext,
                                             final SyntheticsClient syntheticsClient) {
        if (callbackContext.getStabilizationRetryTimes() >= MAX_RETRY_TIMES)
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        final GetCanaryRequest getCanaryRequest  = GetCanaryRequest.builder()
                .name(model.getName())
                .build();
        final GetCanaryResponse getCanaryResponse;
        try {
            getCanaryResponse = proxy.injectCredentialsAndInvokeV2(getCanaryRequest,
                    syntheticsClient::getCanary);

            String canaryState = getCanaryResponse.canary().status().stateAsString();
                if (canaryState.compareTo(CanaryStates.RUNNING.toString()) == 0
                    ||  canaryState.compareTo(CanaryStates.STOPPED.toString()) == 0) {
                return true;
            }
        } catch (final ResourceNotFoundException e) {
            return false;
        }
        return false;
    }

    private SdkBytes compressRawScript(Code code) {
        // Handler name is in the format <function_name>.handler.
        // Need to strip out the .handler suffix

        String functionName = code.getHandler().split("\\.")[0];

        String jsFunctionName = functionName + JS_SUFFIX;
        String zipOutputFilePath = NODE_MODULES_DIR + jsFunctionName;
        String script = code.getScript();

        ByteArrayOutputStream byteArrayOutputStream = null;
        InputStream inputStream = null;
        ZipOutputStream zipByteOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            zipByteOutputStream = new ZipOutputStream(byteArrayOutputStream);
            inputStream = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));

            ZipEntry zipEntry = new ZipEntry(zipOutputFilePath);
            zipByteOutputStream.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int len;

            while ((len = inputStream.read(buffer)) > 0) {
                zipByteOutputStream.write(buffer, 0, len);
            }
            zipByteOutputStream.closeEntry();
            zipByteOutputStream.close();
            inputStream.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return SdkBytes.fromByteBuffer(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
    }

}
