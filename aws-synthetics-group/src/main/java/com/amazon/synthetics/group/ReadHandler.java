package com.amazon.synthetics.group;


import java.util.List;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;

public class ReadHandler extends BaseHandlerStd {

    public ReadHandler() {
        super(Action.READ);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        try {
            Group group = getGroupOrThrow();
            List<String> relatedResources = getGroupResourcesOrThrow();
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
