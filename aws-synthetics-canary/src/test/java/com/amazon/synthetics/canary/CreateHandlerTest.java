package com.amazon.synthetics.canary;

import org.apache.commons.collections.map.SingletonMap;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.proxy.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class CreateHandlerTest extends TestBase {
    private CreateHandler handler = new CreateHandler();

    @Test
    public void handleRequest_returnsInProgress() {
        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isNotNull();

        verify(proxy).injectCredentialsAndInvokeV2(any(CreateCanaryRequest.class), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsCreating_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.CREATING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsError_fails() {
        configureGetCanaryResponse(CanaryState.ERROR, ERROR_STATE_REASON);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsReady_startCanaryAfterCreationIsFalse_returnsSuccess() {
        configureGetCanaryResponse(CanaryState.READY);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsReady_startCanaryAfterCreationIsTrue_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.READY);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();

        verify(proxy).injectCredentialsAndInvokeV2(eq(StartCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsFalse_returnsSuccess() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsTrue_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }



    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel())
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryCreateStarted(true)
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();

        doReturn(getCanaryResponse,
                tagResourceResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleSuccessRemovingOptionalValues() {
        ResourceModel model = buildModel(false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
                .timeoutInSeconds(60)
                .memoryInMB(1024)
                .activeTracing(false)
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .runConfig(outputExpected)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
                getCanaryResponse,
                tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig()).isNull();
    }

    @Test
    public void handleRequest_createCanary_inProgress() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel())
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
                getCanaryResponse,
                tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_createCanary_withoutActiveTracing() {
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        model.setRunConfig(runConfig);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
                .timeoutInSeconds(60)
                .memoryInMB(1024)
                .activeTracing(false)
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .runConfig(outputExpected)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
                getCanaryResponse,
                tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig().getTimeoutInSeconds()).isEqualTo(60);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isNull();
    }

    @Test
    public void handleRequest_createCanary_withActiveTracingTrue() {
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(true);
        model.setRunConfig(runConfig);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
                .timeoutInSeconds(60)
                .memoryInMB(1024)
                .activeTracing(true)
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .runConfig(outputExpected)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
                getCanaryResponse,
                tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig().getTimeoutInSeconds()).isEqualTo(60);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(true);
    }

    @Test
    public void handleRequest_createCanary_withActiveTracingFalse() {
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        model.setRunConfig(runConfig);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
                .timeoutInSeconds(60)
                .memoryInMB(1024)
                .activeTracing(false)
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .runConfig(outputExpected)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
                getCanaryResponse,
                tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig().getTimeoutInSeconds()).isEqualTo(60);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
    }

    @Test
    public void handleRequest_createCanary_withNoEnvironmentVariables() {
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        model.setRunConfig(runConfig);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
            .timeoutInSeconds(60)
            .memoryInMB(1024)
            .activeTracing(false)
            .build();

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .schedule(canaryScheduleOutputForTesting())
            .runConfig(outputExpected)
            .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
            .canary(canary)
            .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
            getCanaryResponse,
            tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig().getTimeoutInSeconds()).isEqualTo(60);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
    }

    @Test
    public void handleRequest_createCanary_withEnvironmentVariables() {
        ResourceModel model = buildModel();
        Map<String, String> environmentVariablesMap = new HashMap<>();
        environmentVariablesMap.put("env_key", "env_val");
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        runConfig.setEnvironmentVariables(environmentVariablesMap);
        model.setRunConfig(runConfig);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
            .timeoutInSeconds(60)
            .memoryInMB(1024)
            .activeTracing(false)
            .build();

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .schedule(canaryScheduleOutputForTesting())
            .runConfig(outputExpected)
            .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
            .canary(canary)
            .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();
        // final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().resourceArn("arn:aws:synthetics:us-west-1:440056434621:canary:canarytestname").tags(sampleTags()).build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse,
            getCanaryResponse,
            tagResourceResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getRunConfig().getTimeoutInSeconds()).isEqualTo(60);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
    }
}
