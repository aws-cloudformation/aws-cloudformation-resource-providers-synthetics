package com.amazon.synthetics.group;

import java.time.Duration;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.AssociateResourceRequest;
import software.amazon.awssdk.services.synthetics.model.AssociateResourceResponse;
import software.amazon.awssdk.services.synthetics.model.ConflictException;
import software.amazon.awssdk.services.synthetics.model.CreateGroupRequest;
import software.amazon.awssdk.services.synthetics.model.CreateGroupResponse;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {
    @Test
    public void handleRequest_inProgress_withNoCanaryArns() {
        final CreateHandler handler = new CreateHandler();
        CreateGroupResponse createGroupResponse = CreateGroupResponse.builder().group(Group.builder()
            .name(GROUP_NAME_FOR_TEST).arn("arn:test").id("testId").build()).build();
        doReturn(createGroupResponse)
            .when(proxyClient.client()).createGroup(any(CreateGroupRequest.class));

        final ResourceModel model = ResourceModel.builder().name("test-group").build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, createFirstCallBackContext(), proxyClient,
            logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

    }

    @Test
    public void handleRequest_inProgress_withCanaryArns() {
        final CreateHandler handler = new CreateHandler();

        when(syntheticsClient.createGroup(any(CreateGroupRequest.class)))
            .thenReturn(CreateGroupResponse.builder().group(Group.builder()
                .name(GROUP_NAME_FOR_TEST).arn("arn:test").id("testId").build()).build());

        final ResourceModel model = ResourceModel.builder()
            .name(GROUP_NAME_FOR_TEST)
            .resourceArns(generateListOfCanaryArns())
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_inProgress_withGroupCreationDone_withCanaryArns() {
        final CreateHandler handler = new CreateHandler();

        when(syntheticsClient.associateResource(any(AssociateResourceRequest.class)))
            .thenReturn(AssociateResourceResponse.builder().build());

        final ResourceModel model = ResourceModel.builder()
            .name(GROUP_NAME_FOR_TEST)
            .resourceArns(generateListOfCanaryArns())
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            CallbackContext.builder().groupCreationStarted(true).build(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getCallbackContext().getAddResourceListIndex()).isEqualTo(1);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Adding resources to the group is in progress");
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_inProgress_withGroupAssociationInProgress_withCanaryArns() {
        final CreateHandler handler = new CreateHandler();

        when(syntheticsClient.associateResource(any(AssociateResourceRequest.class)))
            .thenReturn(AssociateResourceResponse.builder().build());

        final ResourceModel model = ResourceModel.builder()
            .name(GROUP_NAME_FOR_TEST)
            .resourceArns(generateListOfCanaryArns())
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            CallbackContext.builder().groupCreationStarted(true).groupAssociationStarted(true)
                .addResourceListIndex(2).build(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getCallbackContext().getAddResourceListIndex()).isEqualTo(3);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Adding resources to the group is in progress");
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_inProgress_withGroupAssociationDone_withCanaryArns() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .name(GROUP_NAME_FOR_TEST)
            .resourceArns(generateListOfCanaryArns())
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            CallbackContext.builder().groupCreationStarted(true).groupAssociationStarted(true)
                .addResourceListIndex(model.getResourceArns().size()).build(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_whenGroupExists_withCanaryArns() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .name("test-group")
            .resourceArns(generateListOfCanaryArns())
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        when(syntheticsClient.createGroup(any(CreateGroupRequest.class)))
            .thenThrow(ConflictException.builder().message("already exists").build());

        assertThrows(CfnAlreadyExistsException.class, () -> handler.handleRequest(proxy, request,
            createFirstCallBackContext(), proxyClient, logger));
    }
}
