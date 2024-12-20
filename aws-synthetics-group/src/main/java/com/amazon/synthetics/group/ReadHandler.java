package com.amazon.synthetics.group;


import java.util.List;
import java.util.Map;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {

    public ReadHandler() {
        super(Action.READ);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
            final ProxyClient<SyntheticsClient> proxyClient,
            final Logger logger) {
        try {
            ResourceModel model = request.getDesiredResourceState();
            Group group = getGroupOrThrow(proxy, proxyClient, model, logger);
            List<String> relatedResources = getGroupResourcesOrThrow(proxy, proxyClient, model, logger);
            ResourceModel outputModel = Translator.translateFromReadResponse(group, relatedResources);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(outputModel)
                .status(OperationStatus.SUCCESS)
                .build();
        } catch (CfnResourceConflictException e) {
            return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
        }
    }
}
