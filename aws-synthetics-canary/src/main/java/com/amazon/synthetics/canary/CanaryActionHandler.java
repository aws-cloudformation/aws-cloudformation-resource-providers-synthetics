package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class CanaryActionHandler extends BaseHandler<CallbackContext> {
    private final Action action;
    private ActionLogger logger;

    protected AmazonWebServicesClientProxy proxy;
    protected ResourceHandlerRequest<ResourceModel> request;
    protected CallbackContext context;
    protected ResourceModel model;
    protected SyntheticsClient syntheticsClient;
    protected LambdaClient lambdaClient;

    public CanaryActionHandler(Action action) {
        this.action = action;
    }

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                             final ResourceHandlerRequest<ResourceModel> request,
                                                                             final CallbackContext callbackContext,
                                                                             final Logger logger) {
        this.proxy = proxy;
        this.request = request;
        this.context = callbackContext != null ? callbackContext : CallbackContext.builder().build();
        this.model = request.getDesiredResourceState();
        this.logger = new ActionLogger(logger, action, request.getAwsAccountId(), context, model);
        this.syntheticsClient = ClientBuilder.getSyntheticsClient();
        this.lambdaClient = ClientBuilder.getLambdaClient();

        log("Invoking handler");
        ProgressEvent<ResourceModel, CallbackContext> response;
        try {
            response = handleRequest();
        } catch (Exception e) {
            log(e);
            throw e;
        }
        log("Handler invoked");
        return response;
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest();

    protected Canary getCanaryOrThrow() {
        return CanaryHelper.getCanaryOrThrow(proxy, syntheticsClient, model);
    }
    protected Canary getCanaryOrNull() {
        return CanaryHelper.getCanaryOrNull(proxy, syntheticsClient, model.getName());
    }

    protected void log(String message) {
        logger.log(message);
    }
    protected void log(Exception exception) {
        logger.log(exception);
    }

    protected void throwIfRetryLimitExceeded(int retryCount, String retryKey) {
        context.throwIfRetryLimitExceeded(retryCount, retryKey, model);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> waitingForCanaryStateTransition(String message, int retryCount, String retryKey) {
        return waitingForCanaryStateTransition(message, message, retryCount, retryKey);
    }
    protected ProgressEvent<ResourceModel, CallbackContext> waitingForCanaryStateTransition(String message, String log, int retryCount, String retryKey) {
        throwIfRetryLimitExceeded(retryCount, retryKey);
        log(message);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(model)
            .callbackContext(context)
            .message(message)
            .status(OperationStatus.IN_PROGRESS)
            .callbackDelaySeconds(5)
            .build();
    }
}
