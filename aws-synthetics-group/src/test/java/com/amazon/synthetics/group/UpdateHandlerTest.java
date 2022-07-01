package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import java.time.Duration;
import java.util.List;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.AssociateResourceRequest;
import software.amazon.awssdk.services.synthetics.model.AssociateResourceResponse;
import software.amazon.awssdk.services.synthetics.model.DisassociateResourceRequest;
import software.amazon.awssdk.services.synthetics.model.DisassociateResourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Test
    public void handleRequest_withNoChange() {
        configureGetGroupResponse(GROUP_NAME_FOR_TEST);
        List<String> canaryArns = generateListOfCanaryArns();
        configureGetGroupResourcesResponse(canaryArns);

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .name("test-group")
            .resourceArns(canaryArns)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        try {
            ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
                createFirstCallBackContext(), proxyClient, logger);
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
            assertThat(response.getCallbackContext().getAddResourceList().isEmpty());
            assertThat(response.getCallbackDelaySeconds()).isEqualTo(Constants.DEFAULT_CALLBACK_DELAY_SECONDS);
            assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
            assertThat(response.getResourceModels()).isNull();
            assertThat(response.getMessage()).isEqualTo("Creating a diff for update completed");
            assertThat(response.getErrorCode()).isNull();

            response = handler.handleRequest(proxy, request,
                response.getCallbackContext(), logger);
            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Test
    public void handleRequest_withOnlyAdditions() {
        configureGetGroupResponse(GROUP_NAME_FOR_TEST);
        List<String> canaryArns = generateListOfCanaryArns();
        configureGetGroupResourcesResponse(canaryArns.subList(0, 19));

        when(syntheticsClient.associateResource(any(AssociateResourceRequest.class)))
            .thenReturn(AssociateResourceResponse.builder().build());

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .name("test-group")
            .resourceArns(canaryArns)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext().getAddResourceList().size()).isEqualTo(1);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(Constants.DEFAULT_CALLBACK_DELAY_SECONDS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Creating a diff for update completed");
        assertThat(response.getErrorCode()).isNull();

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }

    @Test
    public void handleRequest_withOnlyRemovals() {
        configureGetGroupResponse(GROUP_NAME_FOR_TEST);
        List<String> canaryArns = generateListOfCanaryArns();
        configureGetGroupResourcesResponse(canaryArns);

        when(syntheticsClient.disassociateResource(any(DisassociateResourceRequest.class)))
            .thenReturn(DisassociateResourceResponse.builder().build());

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .name("test-group")
            .resourceArns(canaryArns.subList(0, 19))
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext().getRemoveResourceList().size()).isEqualTo(1);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(Constants.DEFAULT_CALLBACK_DELAY_SECONDS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Creating a diff for update completed");
        assertThat(response.getErrorCode()).isNull();

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }

    @Test
    public void handleRequest_withAddAndRemove() {
        configureGetGroupResponse(GROUP_NAME_FOR_TEST);
        List<String> canaryArns = generateListOfCanaryArns();
        configureGetGroupResourcesResponse(canaryArns);
        when(syntheticsClient.associateResource(any(AssociateResourceRequest.class)))
            .thenReturn(AssociateResourceResponse.builder().build());
        when(syntheticsClient.disassociateResource(any(DisassociateResourceRequest.class)))
            .thenReturn(DisassociateResourceResponse.builder().build());

        final UpdateHandler handler = new UpdateHandler();
        canaryArns = canaryArns.subList(0, 19);
        canaryArns.add("new-arn");

        final ResourceModel model = ResourceModel.builder()
            .name("test-group")
            .resourceArns(canaryArns)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext().getAddResourceList().size()).isEqualTo(1);
        assertThat(response.getCallbackContext().getRemoveResourceList().size()).isEqualTo(1);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(Constants.DEFAULT_CALLBACK_DELAY_SECONDS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Creating a diff for update completed");
        assertThat(response.getErrorCode()).isNull();

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getCallbackContext().getAddResourceList().isEmpty());
        assertThat(response.getCallbackContext().getRemoveResourceList().size()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getCallbackContext().getRemoveResourceList().isEmpty());
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        response = handler.handleRequest(proxy, request,
            response.getCallbackContext(), proxyClient, logger);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }
}
