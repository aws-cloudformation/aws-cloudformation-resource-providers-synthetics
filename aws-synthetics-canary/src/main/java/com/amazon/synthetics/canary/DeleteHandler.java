package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private static final int MAX_RETRY_TIMES = 10;

    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();
        SyntheticsClient syntheticsClient = ClientBuilder.getClient();

        CallbackContext currentContext = callbackContext != null ?
            callbackContext : CallbackContext.builder().build();

        logger.log(String.format("[DELETE] Delete handler called for canary %s. RetryKey = %s and RemainingRetryCount = %d.",
            model.getName(), currentContext.getRetryKey(), currentContext.getRemainingRetryCount()));

        Canary canary = CanaryHelper.getCanaryOrThrow(proxy, syntheticsClient, model);
        if (canary.status().state() == CanaryState.CREATING) {
            String message = String.format("[DELETE] Canary %s is in state CREATING and cannot be deleted.", canary.name());
            logger.log(message);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .message(message)
                .errorCode(HandlerErrorCode.ResourceConflict)
                .status(OperationStatus.FAILED)
                .build();
        } else if (canary.status().state() == CanaryState.STARTING) {
            String message = String.format("[DELETE] Canary %s is in state STARTING. It must finish starting before it can be stopped and deleted.", canary.name());
            return waitingForCanaryStateTransition(message, currentContext, model);
        } else if (canary.status().state() == CanaryState.UPDATING) {
            String message = String.format("[DELETE] Canary %s is in state UPDATING. It must finish updating before it can be deleted.", canary.name());
            return waitingForCanaryStateTransition(message, currentContext, model);
        } else if (canary.status().state() == CanaryState.STOPPING) {
            String message = String.format("[DELETE] Canary %s is in state STOPPING. It must finish stopping before it can be deleted.", canary.name());
            return waitingForCanaryStateTransition(message, currentContext, model);
        } else if (canary.status().state() == CanaryState.RUNNING) {
            String message = String.format("[DELETE] Canary %s is in state RUNNING. It must be stopped before it can be deleted.", canary.name());

            try {
                // Handle race condition where an external process calls StopCanary before we do.
                proxy.injectCredentialsAndInvokeV2(
                    StopCanaryRequest.builder()
                        .name(canary.name())
                        .build(),
                    syntheticsClient::stopCanary);
            } catch (ConflictException e) {
                logger.log(String.format("[DELETE] Caught ConflictException when trying to stop canary %s.", canary.name()));
            }

            return waitingForCanaryStateTransition(message, currentContext, model);
        } else {
            // The canary will be deleted once DeleteCanary returns.
            logger.log(String.format("[DELETE] Deleting canary %s.", canary.name()));

            try {
                proxy.injectCredentialsAndInvokeV2(
                    DeleteCanaryRequest.builder()
                        .name(canary.name())
                        .build(),
                    syntheticsClient::deleteCanary);
            } catch (ResourceNotFoundException e) {
                // Handle race condition where an external process calls DeleteCanary before we do.
                if (CanaryHelper.getCanaryOrNull(proxy, syntheticsClient, canary.name()) == null) {
                    // Success
                    return ProgressEvent.defaultSuccessHandler(null);
                }
            } catch (ConflictException e) {
                // Handle race condition where an external process is mutating the canary while we
                // are trying to delete it.
                throw new CfnResourceConflictException(
                    ResourceModel.TYPE_NAME,
                    canary.name(),
                    "The canary state changed unexpectedly.",
                    e);
            }

            logger.log(String.format("[DELETE] Deleted canary %s.", canary.name()));
            return ProgressEvent.defaultSuccessHandler(null);
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext> waitingForCanaryStateTransition(String message, CallbackContext context, ResourceModel model) {
        context.throwIfRetryLimitExceeded(MAX_RETRY_TIMES, message, model);
        logger.log(message);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(model)
            .message(message)
            .status(OperationStatus.IN_PROGRESS)
            .callbackDelaySeconds(5)
            .build();
    }
}
