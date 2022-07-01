package com.amazon.synthetics.group;

import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.proxy.Logger;

public class GroupLogger {
    private final Logger logger;
    private final Action action;
    private final String awsAccountId;
    private final CallbackContext context;
    private final ResourceModel model;

    public GroupLogger(Logger logger, Action action, String awsAccountId, CallbackContext context, ResourceModel model) {
        this.logger = logger;
        this.action = action;
        this.awsAccountId = awsAccountId;
        this.context = context;
        this.model = model;
    }

    public void log(String message) {
        logger.log(message);
    }

    public void log(Exception exception) {
        logger.log(exception.getMessage());
    }
}
