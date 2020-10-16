package com.amazon.synthetics.canary;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;

@Data
@Builder
@JsonDeserialize(builder = CallbackContext.CallbackContextBuilder.class)
public class CallbackContext {
    private boolean canaryCreateStarted;
    private boolean canaryUpdateStarted;
    private boolean canaryDeleteStarted;
    private String retryKey;
    private int remainingRetryCount;
    private CanaryState initialCanaryState;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CallbackContextBuilder {
    }

    public void throwIfRetryLimitExceeded(int retryCount, String retryKey, ResourceModel model) {
        if (!Objects.equals(this.retryKey, retryKey)) {
            this.retryKey = retryKey;
            remainingRetryCount = retryCount;
        }

        --remainingRetryCount;
        if (remainingRetryCount == 0) {
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getName());
        }
    }
}
