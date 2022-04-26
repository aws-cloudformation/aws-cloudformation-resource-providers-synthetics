package com.amazon.synthetics.canary;

import org.apache.commons.collections.map.SingletonMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class UpdateHandlerTest extends TestBase {
    private UpdateHandler handler = new UpdateHandler();

    @Test
    public void handleRequest_canaryStateIsCreating_fails() {
        configureGetCanaryResponse(CanaryState.CREATING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ResourceConflict);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_canaryStateIsUpdating_fails() {
        configureGetCanaryResponse(CanaryState.UPDATING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ResourceConflict);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_canaryStateIsStarting_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_canaryStateIsStopping_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STOPPING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllowsUpdate_updateStarts(CanaryState state) {
        configureGetCanaryResponse(state);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, REQUEST, null, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getCallbackContext().isCanaryUpdateStarted()).isTrue();
        assertThat(response.getCallbackContext().getInitialCanaryState()).isEqualTo(state);
    }

    @Test
    public void handleRequest_canaryNotFound_throws() {
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, REQUEST, null, logger))
            .isInstanceOf(CfnNotFoundException.class);
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsUpdating_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.UPDATING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsError_fails() {
        configureGetCanaryResponse(CanaryState.ERROR, ERROR_STATE_REASON);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = { "READY", "STOPPED" })
    public void handleRequest_inProgress_canaryStateIsReadyOrStopped_startCanaryAfterCreationIsFalse_returnsSuccess(CanaryState state) {
        configureGetCanaryResponse(state);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = { "READY", "STOPPED" })
    public void handleRequest_inProgress_canaryStateIsReadyOrStopped_startCanaryAfterCreationIsTrue_returnsInProgress(CanaryState state) {
        configureGetCanaryResponse(state);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();

        verify(proxy).injectCredentialsAndInvokeV2(eq(StartCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsFalse_returnsSuccess() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsTrue_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryUpdateStarted(true).build(), logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsRunning_initialStateIsRunning_startCanaryAfterCreationIsTrue_returnsSuccess() {
        configureGetCanaryResponse(CanaryState.RUNNING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy,
            REQUEST_START_CANARY,
            CallbackContext.builder().canaryUpdateStarted(true).initialCanaryState(CanaryState.RUNNING).build(),
            logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsRunning_initialStateIsRunning_startCanaryAfterCreationIsFalse_stopsCanary() {
        configureGetCanaryResponse(CanaryState.RUNNING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy,
            REQUEST,
            CallbackContext.builder().canaryUpdateStarted(true).initialCanaryState(CanaryState.RUNNING).build(),
            logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();

        verify(proxy).injectCredentialsAndInvokeV2(eq(StopCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsRunning_initialStateIsRunning_startCanaryAfterCreationIsTrue_stateReasonIsNotNull_fails() {
        configureGetCanaryResponse(CanaryState.RUNNING, ERROR_STATE_REASON);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy,
            REQUEST_START_CANARY,
            CallbackContext.builder().canaryUpdateStarted(true).initialCanaryState(CanaryState.RUNNING).build(),
            logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = { "READY", "STOPPED", "ERROR" })
    public void handleRequest_inProgress_canaryStateIsRunning_initialStateIsNotRunning_returnsSuccess(CanaryState initialState) {
        configureGetCanaryResponse(CanaryState.RUNNING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy,
            REQUEST,
            CallbackContext.builder().canaryUpdateStarted(true).initialCanaryState(initialState).build(),
            logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStopping_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STOPPING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy,
            REQUEST,
            CallbackContext.builder().canaryUpdateStarted(true).build(),
            logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-1.0", null))
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdateStarted(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

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
    public void handleRequest_updateRuntime(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getRuntimeVersion()).isEqualTo("syn-nodejs-2.0-beta");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateWithRemovedTimeout(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", null, true, false, false))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getRuntimeVersion()).isEqualTo("syn-nodejs-2.0-beta");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateRuntimeDownVersion(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-1.0", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getRuntimeVersion()).isEqualTo("syn-1.0");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateDuration(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingToTrue(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", true))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingToFalse(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingFromTrueToFalse(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_durationInSecondsNull() {
        ResourceModel model = buildModel("syn-nodejs-2.0-beta", true);
        model.getSchedule().setDurationInSeconds(null);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .executionRoleArn("test execution arn")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
            .schedule(canaryScheduleOutputWithNullDurationForTesting())
            .runtimeVersion("syn-nodejs-2.0-beta")
            .tags(tagExisting)
            .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();

        doReturn(getCanaryResponse)
            .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }


    @Test
    public void handleRequest_updateEnvironmentVariablesFromNullToPresent(){
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

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .executionRoleArn("test execution arn")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
            .schedule(canaryScheduleOutputWithNullDurationForTesting())
            .runtimeVersion("syn-nodejs-2.0-beta")
            .tags(tagExisting)
            .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();

        doReturn(getCanaryResponse)
            .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateEnvironmentVariablesFromPresentToEmpty(){
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        runConfig.setEnvironmentVariables(new HashMap<>());
        model.setRunConfig(runConfig);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .executionRoleArn("test execution arn")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
            .schedule(canaryScheduleOutputWithNullDurationForTesting())
            .runtimeVersion("syn-nodejs-2.0-beta")
            .tags(tagExisting)
            .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();

        doReturn(getCanaryResponse)
            .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }


    @Test
    public void handleRequest_updateVisualReferenceFromNullToValid(){
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        model.setRunConfig(runConfig);
        VisualReference visualReference = new VisualReference();
        BaseScreenshot baseScreenshot = new BaseScreenshot();
        List<BaseScreenshot> baseScreenshotList = new ArrayList<>();
        baseScreenshot.setScreenshotName("Test-base.png");
        baseScreenshotList.add(baseScreenshot);
        visualReference.setBaseScreenshots(baseScreenshotList);
        visualReference.setBaseCanaryRunId("nextrun");
        model.setVisualReference(visualReference);

        VisualReferenceOutput visualReferenceExisting = null;
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-3.2")
                .visualReference(visualReferenceExisting)
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getVisualReference()).isNotNull();
    }


    @Test
    public void handleRequest_updateVisualReferenceToNull(){
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        model.setRunConfig(runConfig);
        VisualReference visualReference = null;
        model.setVisualReference(visualReference);
        VisualReferenceOutput visualReferenceExisting = null;
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-3.2")
                .visualReference(visualReferenceExisting)
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getVisualReference()).isNull();
    }

    @Test
    public void handleRequest_updateVisualReferenceBaseScreenshotsToNull(){
        ResourceModel model = buildModel();
        RunConfig runConfig = new RunConfig();
        runConfig.setTimeoutInSeconds(60);
        runConfig.setMemoryInMB(1024);
        runConfig.setActiveTracing(false);
        model.setRunConfig(runConfig);

        VisualReference visualReference = new VisualReference();
        BaseScreenshot baseScreenshot = new BaseScreenshot();
        List<BaseScreenshot> baseScreenshotList = null;
        visualReference.setBaseScreenshots(baseScreenshotList);
        visualReference.setBaseCanaryRunId("nextrun");
        model.setVisualReference(visualReference);

        model.setVisualReference(visualReference);
        VisualReferenceOutput visualReferenceExisting = null;
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-3.2")
                .visualReference(visualReferenceExisting)
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getVisualReference()).isNotNull();
        assertThat(response.getResourceModel().getVisualReference().getBaseScreenshots()).isNull();
    }

    @Test
    public void handleRequest_updateArtifactEncryptionFromNullToPresent() {
        ResourceModel model = buildModel();

        final ArtifactConfig artifactConfig = new ArtifactConfig();
        final S3Encryption s3Encryption = new S3Encryption();
        s3Encryption.setEncryptionMode("SSE_KMS");
        s3Encryption.setKmsKeyArn("arn:aws:kms:us-west-2:222222222222:key/kmsKeyId");
        artifactConfig.setS3Encryption(s3Encryption);
        model.setArtifactConfig(artifactConfig);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);
        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getEncryptionMode()).isEqualTo("SSE_KMS");
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getKmsKeyArn()).isEqualTo("arn:aws:kms:us-west-2:222222222222:key/kmsKeyId");
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateArtifactEncryptionFromPresentToEmpty() {
        ResourceModel model = buildModel();
        final ArtifactConfig artifactConfig = new ArtifactConfig();
        final S3Encryption s3Encryption = new S3Encryption();
        artifactConfig.setS3Encryption(s3Encryption);
        model.setArtifactConfig(artifactConfig);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(false).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .artifactConfig(ArtifactConfigOutput.builder()
                        .s3Encryption(S3EncryptionConfig.builder()
                                .encryptionMode(EncryptionMode.SSE_KMS)
                                .kmsKeyArn("arn:aws:kms:us-west-2:222222222222:key/kmsKeyId").build()).build())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getEncryptionMode()).isNull();
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getKmsKeyArn()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateArtifactEncryptionFromPresentToNull() {
        ResourceModel model = buildModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(false).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .artifactConfig(ArtifactConfigOutput.builder()
                        .s3Encryption(S3EncryptionConfig.builder()
                                .encryptionMode(EncryptionMode.SSE_KMS)
                                .kmsKeyArn("arn:aws:kms:us-west-2:222222222222:key/kmsKeyId").build()).build())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getArtifactConfig()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateArtifactEncryptionFromKmsEncryptionToSSES3() {
        ResourceModel model = buildModel();

        final ArtifactConfig artifactConfig = new ArtifactConfig();
        final S3Encryption s3Encryption = new S3Encryption();
        s3Encryption.setEncryptionMode("SSE_S3");
        artifactConfig.setS3Encryption(s3Encryption);
        model.setArtifactConfig(artifactConfig);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(false).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .artifactConfig(ArtifactConfigOutput.builder()
                        .s3Encryption(S3EncryptionConfig.builder()
                                .encryptionMode(EncryptionMode.SSE_KMS)
                                .kmsKeyArn("arn:aws:kms:us-west-2:222222222222:key/kmsKeyId").build()).build())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder().build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getEncryptionMode()).isEqualTo("SSE_S3");
        assertThat(response.getResourceModel().getArtifactConfig().getS3Encryption().getKmsKeyArn()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
