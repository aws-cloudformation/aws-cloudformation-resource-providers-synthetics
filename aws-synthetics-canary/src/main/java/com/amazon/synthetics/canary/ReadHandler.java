package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.proxy.*;

public class ReadHandler extends CanaryActionHandler {
    public ReadHandler() {
        super(Action.READ);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        Canary canary = getCanaryOrThrow();
        ResourceModel outputModel = ModelHelper.constructModel(canary, model);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(outputModel)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
