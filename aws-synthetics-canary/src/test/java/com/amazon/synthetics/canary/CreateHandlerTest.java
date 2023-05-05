package com.amazon.synthetics.canary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

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
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
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
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    public void handleRequest_inProgress_canaryStateIsReady_startCanaryAfterCreationIsFalseOrNull_returnsSuccess(Boolean startAfterCreate) {
        configureGetCanaryResponse(CanaryState.READY);
        ResourceHandlerRequest<ResourceModel> request = startAfterCreate == null ? REQUEST_NULL_START_CANARY : REQUEST;
        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, request, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsReady_startCanaryAfterCreationIsTrue_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.READY);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();

        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(StartCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsFalseOrNull_returnsSuccess(Boolean value) {
        configureGetCanaryResponse(CanaryState.STARTING);
        ResourceHandlerRequest<ResourceModel> request = value == null ? REQUEST_NULL_START_CANARY : REQUEST;
        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, request, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModel()).isNotNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_inProgress_canaryStateIsStarting_startCanaryAfterCreationIsTrue_returnsInProgress() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
            proxy, REQUEST_START_CANARY, CallbackContext.builder().canaryCreateStarted(true).build(), logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getMessage()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }



    @Test
    public void handleRequest_SimpleSuccess() {
        ResourceModel model = buildModel(false);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryCreateStarted(true)
                .build();

        final Canary canary = Canary.builder()
                .name(CANARY_NAME)
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .build();
        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getState()).isEqualTo("RUNNING");
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_createCanary_Basic() {
        ResourceModel model = buildModel(false);
        ResourceModel modelClone = buildModel(false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final Canary canary = Canary.builder()
                .name(CANARY_NAME)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(false, model)), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(modelClone);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(false, model)), any());
    }

    @Test
    public void handleRequest_createCanary_WithOptionals() {
        ResourceModel model = buildModel(true);
        ResourceModel modelClone = buildModel(true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final Canary canary = Canary.builder()
                .name(CANARY_NAME)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(true, model)), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(modelClone);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(true, model)), any());
    }

    // need a separate test for this field because we can either supply code or s3 bucket with s3 key
    @Test
    public void handleRequest_createCanary_WithS3Bucket() {
        ResourceModel model = buildModel(true);
        ResourceModel modelClone = buildModel(true);
        final Code code = new Code("s3bucket",
                "s3Key",
                null,
                null,
                "pageLoadBlueprint.handler",
                null);
        final Code codeClone = new Code("s3bucket",
                "s3Key",
                null,
                null,
                "pageLoadBlueprint.handler",
                null);
        model.setCode(code);
        modelClone.setCode(codeClone);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        CanaryRunConfigOutput outputExpected = CanaryRunConfigOutput.builder()
                .timeoutInSeconds(60)
                .memoryInMB(1024)
                .activeTracing(false)
                .build();

        // does not matter what we return, as we ignore canary response
        final Canary canary = Canary.builder()
                .name(CANARY_NAME)
                .build();

        final CreateCanaryResponse createCanaryResponse = CreateCanaryResponse.builder()
                .canary(canary)
                .build();
        final CallbackContext inputContext = CallbackContext.builder().build();
        final CallbackContext outputContext = CallbackContext.builder().canaryCreateStarted(true).build();

        doReturn(createCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(true, model)), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, inputContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(outputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(modelClone);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxy).injectCredentialsAndInvokeV2(eq(buildCreateCanaryRequest(true, model)), any());
    }
}

