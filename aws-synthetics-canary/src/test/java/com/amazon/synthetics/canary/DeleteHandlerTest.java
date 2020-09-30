package com.amazon.synthetics.canary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.CanaryStatus;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.DeleteCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.StopCanaryRequest;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteHandlerTest extends TestBase {
    private static final ResourceHandlerRequest<ResourceModel> REQUEST = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(ResourceModel.builder()
            .name(CANARY_NAME)
            .build())
        .build();

    private DeleteHandler handler = new DeleteHandler();

    @Test
    public void handleRequest_canaryStateIsCreating_fails() {
        configureGetCanaryResponse(CanaryState.CREATING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ResourceConflict);
    }

    @Test
    public void handleRequest_canaryStateIsStarting_inProgress() {
        configureGetCanaryResponse(CanaryState.STARTING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_canaryStateIsUpdating_inProgress() {
        configureGetCanaryResponse(CanaryState.UPDATING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_canaryStateIsStopping_inProgress() {
        configureGetCanaryResponse(CanaryState.STOPPING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_canaryStateIsRunning_invokesStopCanary() {
        configureGetCanaryResponse(CanaryState.RUNNING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(StopCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_canaryStateIsRunning_invokesStopCanary_handlesConflict() {
        configureGetCanaryResponse(CanaryState.RUNNING);
        when(proxy.injectCredentialsAndInvokeV2(eq(StopCanaryRequest.builder().name(CANARY_NAME).build()), any()))
            .thenThrow(ConflictException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(StopCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_success(CanaryState state) {
        configureGetCanaryResponse(state);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_handlesAlreadyDeleted_success(CanaryState state) {
        configureGetCanaryResponse(state);
        when(proxy.injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME).build()), any()))
            .thenThrow(ResourceNotFoundException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_handlesConflict_fails(CanaryState state) {
        configureGetCanaryResponse(state);
        when(proxy.injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME).build()), any()))
            .thenThrow(ConflictException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, REQUEST, null, logger))
            .isInstanceOf(CfnResourceConflictException.class);

        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME).build()), any());
    }

    @Test
    public void handleRequest_canaryDoesNotExist_fails() {
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, REQUEST, null, logger))
            .isInstanceOf(CfnNotFoundException.class);
    }
}
