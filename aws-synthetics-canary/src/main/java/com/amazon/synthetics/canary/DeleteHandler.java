package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends CanaryActionHandler {
    private static final int MAX_RETRY_TIMES = 20;

    public DeleteHandler() {
        super(Action.DELETE);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        if (context.isCanaryDeleteStarted()) {
            return confirmCanaryDeleted();
        }

        Canary canary = getCanaryOrThrow();

        if (canary.status().state() == CanaryState.CREATING) {
            String message = "Canary is in state CREATING and cannot be deleted.";
            log(message);
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .message(message)
                .errorCode(HandlerErrorCode.ResourceConflict)
                .status(OperationStatus.FAILED)
                .build();
        } else if (canary.status().state() == CanaryState.STARTING) {
            String message = "Canary is in state STARTING. It must finish starting before it can be stopped and deleted.";
            return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "STARTING");
        } else if (canary.status().state() == CanaryState.UPDATING) {
            String message = "Canary is in state UPDATING. It must finish updating before it can be deleted.";
            return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "UPDATING");
        } else if (canary.status().state() == CanaryState.STOPPING) {
            String message = "Canary is in state STOPPING. It must finish stopping before it can be deleted.";
            return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "STOPPING");
        } else if (canary.status().state() == CanaryState.RUNNING) {
            return handleCanaryInStateRunning(canary);
        } else if (canary.status().state() == CanaryState.DELETING) {
            return confirmCanaryDeleted();
        } else {
            return deleteCanary(canary);
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleCanaryInStateRunning(Canary canary) {
        String message = "Canary is in state RUNNING. It must be stopped before it can be deleted.";
        try {
            // Handle race condition where an external process calls StopCanary before we do.
            proxy.injectCredentialsAndInvokeV2(
                StopCanaryRequest.builder()
                    .name(canary.name())
                    .build(),
                syntheticsClient::stopCanary);
        } catch (ConflictException e) {
            log("Caught ConflictException when trying to stop canary.");
        }
        return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "RUNNING");
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteCanary(Canary canary) {
        // The canary will be deleted once DeleteCanary returns.
        log("Deleting canary.");
        try {
            proxy.injectCredentialsAndInvokeV2(
                DeleteCanaryRequest.builder()
                    .name(canary.name())
                    .deleteLambda(model.getDeleteLambdaResourcesOnCanaryDeletion() != null ?
                        model.getDeleteLambdaResourcesOnCanaryDeletion() : false)
                    .build(),
                syntheticsClient::deleteCanary);
        } catch (ResourceNotFoundException e) {
            // Handle race condition where an external process calls DeleteCanary before we do.
            return ProgressEvent.defaultSuccessHandler(null);
        } catch (ConflictException e) {
            // Handle race condition where an external process is mutating the canary while we
            // are trying to delete it.
            throw new CfnResourceConflictException(
                ResourceModel.TYPE_NAME,
                canary.name(),
                "The canary state changed unexpectedly.",
                e);
        }

        context.setCanaryDeleteStarted(true);
        log("Deleted canary.");
        return confirmCanaryDeleted();
    }

    private ProgressEvent<ResourceModel, CallbackContext> confirmCanaryDeleted() {
        if (getCanaryOrNull() != null) {
            String message = "Confirming that canary was deleted.";
            return waitingForCanaryStateTransition(message, MAX_RETRY_TIMES, "DELETING");
        } else {
            return ProgressEvent.defaultSuccessHandler(null);
        }
    }
}
