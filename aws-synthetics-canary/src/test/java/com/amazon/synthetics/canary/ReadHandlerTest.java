package com.amazon.synthetics.canary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends TestBase {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void handleRequest_SimpleSuccess(boolean useDefaultS3Encryption) {
        final ReadHandler handler = new ReadHandler();

        configureGetCanaryResponse(canaryResponseObjectForTesting(CANARY_NAME, true, useDefaultS3Encryption));

        ResourceModel outputModel = buildModelForRead(true, useDefaultS3Encryption);

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, buildResourceHandlerRequestWithTagReplication(CANARY_NAME), null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(outputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxy).injectCredentialsAndInvokeV2(
                eq(GetCanaryRequest.builder().name(CANARY_NAME).build()),
                any()
        );
    }

    @Test
    public void handleRequest_SimpleSuccess_NoOptionals() {
        final ReadHandler handler = new ReadHandler();

        configureGetCanaryResponse(canaryResponseObjectForTesting(CANARY_NAME, false, false));

        ResourceModel outputModel = buildModelForRead(false, false);

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, buildResourceHandlerRequestWithName(CANARY_NAME), null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(outputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxy).injectCredentialsAndInvokeV2(
                eq(GetCanaryRequest.builder().name(CANARY_NAME).build()),
                any()
        );
    }

    @Test
    public void handleRequest_CanaryNotFound() {
        final ReadHandler handler = new ReadHandler();

        // if we throw ResourceNotFoundException when getting canary, that error gets caught and CfnNotFoundException is thrown.
        configureGetCanaryResponse(ResourceNotFoundException.builder().build());

        assertThatThrownBy(() -> handler.handleRequest(proxy, buildResourceHandlerRequestWithTagReplication(CANARY_NAME), null, logger))
                .isInstanceOf(CfnNotFoundException.class);

        verify(proxy).injectCredentialsAndInvokeV2(
                eq(GetCanaryRequest.builder().name(CANARY_NAME).build()),
                any()
        );
    }
}
