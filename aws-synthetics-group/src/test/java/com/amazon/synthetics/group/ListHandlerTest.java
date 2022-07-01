package com.amazon.synthetics.group;

import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.services.synthetics.model.GroupSummary;
import software.amazon.awssdk.services.synthetics.model.ListGroupsRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupsResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    //@Test
    public void handleRequest_SimpleSuccess() {
        final ListHandler handler = new ListHandler();
        GroupSummary samplegrp = GroupSummary.builder().arn("arn:testArn").id("groupId").name("grp").build();
        List<GroupSummary> grpList = Arrays.asList(samplegrp);
        when(syntheticsClient.listGroups(any(ListGroupsRequest.class)))
            .thenReturn(ListGroupsResponse.builder().nextToken("hggygg").groups(grpList).build());

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, null, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

    }

    //@Test
    public void handleRequest_SimpleFailure() {
        final ListHandler handler = new ListHandler();

        when(syntheticsClient.listGroups(any(ListGroupsRequest.class)))
            .thenThrow(ResourceNotFoundException.builder().build());

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, null, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

    }
}
