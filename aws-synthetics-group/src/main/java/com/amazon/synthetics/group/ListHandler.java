package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import java.util.Map;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.ListGroupsRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupsResponse;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

public class ListHandler extends BaseHandlerStd {

    public ListHandler() {
        super(Action.LIST);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
            final ProxyClient<SyntheticsClient> proxyClient,
            final Logger logger) {
        ResourceModel model = request.getDesiredResourceState();

        // construct a body of a request
        final ListGroupsRequest listGroupsRequest = Translator.translateToListRequest(request.getNextToken());

        try{
            // make an api call
            ListGroupsResponse listGroupsResponse = proxy.injectCredentialsAndInvokeV2(listGroupsRequest,
                proxyClient.client()::listGroups);

            // get a token for the next page
            String nextToken = listGroupsResponse == null ? null : listGroupsResponse.nextToken();

            // construct resource models
            // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/master/aws-logs-loggroup/src/main/java/software/amazon/logs/loggroup/ListHandler.java#L19-L21
            List<ResourceModel>  models = Translator.translateFromListResponse(listGroupsResponse);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(models)
                .nextToken(nextToken)
                .status(OperationStatus.SUCCESS)
                .build();
        } catch (ValidationException exception) {
            return ProgressEvent.failed(
                model,
                callbackContext,
                HandlerErrorCode.InvalidRequest,
                (exception.getMessage() != null ? exception.getMessage() : Constants.VALIDATION_EXCEPTION_OCCURRED));
        }

    }
}
