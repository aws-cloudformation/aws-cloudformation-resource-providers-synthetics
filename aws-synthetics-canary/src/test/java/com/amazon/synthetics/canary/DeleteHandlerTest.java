package com.amazon.synthetics.canary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.CanaryStateReasonCode;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.DeleteCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.StopCanaryRequest;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteHandlerTest extends TestBase {
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
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_inProgress(CanaryState state) {
        configureGetCanaryResponse(state);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(false).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanaryWithDeleteLambda_inProgress(CanaryState state) {
        configureGetCanaryResponse(state);


        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(true).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanaryWithDeleteLambdaFalse_inProgress(CanaryState state) {
        configureGetCanaryResponse(state);


        ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA_FALSE, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
                .deleteLambda(false).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_handlesAlreadyDeleted_success(CanaryState state) {
        configureGetCanaryResponse(state);
        when(proxy.injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(false).build()), any()))
            .thenThrow(ResourceNotFoundException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(false).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanaryWithDeleteLambda_handlesAlreadyDeleted_success(CanaryState state) {
        configureGetCanaryResponse(state);
        when(proxy.injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(true).build()), any()))
            .thenThrow(ResourceNotFoundException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA, null, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(true).build()), any());
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_canaryStateAllows_invokesDeleteCanary_handlesConflict_fails(CanaryState state) {
        configureGetCanaryResponse(state);
        when(proxy.injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(false).build()), any()))
            .thenThrow(ConflictException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, REQUEST, null, logger))
            .isInstanceOf(CfnResourceConflictException.class);

        verify(proxy).injectCredentialsAndInvokeV2(eq(DeleteCanaryRequest.builder().name(CANARY_NAME)
            .deleteLambda(false).build()), any());
    }

    @Test
    public void handleRequest_confirmCanaryDeleted_canaryExists_inProgress() {
        CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        configureGetCanaryResponse(CanaryState.READY);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, context, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_confirmCanaryDeleted_canaryExists_WithDeleteLambda_inProgress() {
        CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        configureGetCanaryResponse(CanaryState.READY);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA, context, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_confirmCanaryDeleted_canaryExistsInDELETING_WithDeleteLambda_inProgress() {
        CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        configureGetCanaryResponse(CanaryState.DELETING);

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA, context, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    }

    @Test
    public void handleRequest_confirmCanaryDeleted_canaryNotFound_success() {
        CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST, context, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }

    @Test
    public void handleRequest_confirmCanaryDeleted_canaryNotFound_WithDeleteLambda_success() {
        CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, REQUEST_WITH_DELETELAMBDA, context, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }

    @Test
    public void handleRequest_canaryDoesNotExist_fails() {
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, REQUEST, null, logger))
            .isInstanceOf(CfnNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CanaryState.class, names = {"READY", "STOPPED", "ERROR"})
    public void handleRequest_confirmCanaryDeleted_canaryRollbackOccurred_rollbackReasonInResponseMessage(CanaryState state) {
        final CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        final String stateReason = "User is not authorized to perform: lambda:DeleteFunction on resource";
        final Canary canary = createCanaryWithState(state, stateReason, CanaryStateReasonCode.ROLLBACK_COMPLETE);
        configureGetCanaryResponse(canary);
 
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
                proxy, REQUEST, context, logger);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
        assertThat(response.getMessage()).isEqualTo(stateReason);
    }
 
    @Test
    public void handleRequest_confirmCanaryDeleted_canaryDeleteFailed_reasonInResponseMessage() {
        final CallbackContext context = CallbackContext.builder().canaryDeleteStarted(true).build();
        final String stateReason = "User is not authorized to perform: lambda:DeleteLayerVersion on resource";
        final Canary canary = createCanaryWithState(CanaryState.ERROR, stateReason, CanaryStateReasonCode.DELETE_FAILED);
        configureGetCanaryResponse(canary);
 
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(
                proxy, REQUEST, context, logger);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
        assertThat(response.getMessage()).isEqualTo(stateReason);
    }
}
