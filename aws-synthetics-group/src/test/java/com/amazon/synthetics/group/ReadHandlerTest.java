package com.amazon.synthetics.group;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.synthetics.model.GetGroupRequest;
import software.amazon.awssdk.services.synthetics.model.GetGroupResponse;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.ListGroupsRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupsResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Test
    public void handleRequest_SimpleSuccess() {
        final ReadHandler handler = new ReadHandler();
        configureGetGroupResponse(GROUP_NAME_FOR_TEST);

        List<String> resourceArns = generateListOfCanaryArns();

        configureGetGroupResourcesResponse(resourceArns);

        final ResourceModel model = ResourceModel.builder()
            .id("groupId")
            .tags(new ArrayList<>())
            .name(GROUP_NAME_FOR_TEST)
            .resourceArns(resourceArns)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleFailure() {
        final ReadHandler handler = new ReadHandler();
        when(syntheticsClient.getGroup(any(GetGroupRequest.class)))
            .thenThrow(ResourceNotFoundException.builder().build());


        final ResourceModel model = ResourceModel.builder()
            .id("groupId")
            .tags(new ArrayList<>())
            .name(GROUP_NAME_FOR_TEST)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, createFirstCallBackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
