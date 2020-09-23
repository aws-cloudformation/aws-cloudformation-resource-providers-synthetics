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
    private boolean canaryCreationStarted;
    private boolean canaryCreationStablized;
    private boolean canaryStartStarted;
    private boolean canaryStartStablized;

    private int stabilizationRetryTimes;

    private boolean canaryUpdationStarted;
    private boolean canaryUpdationStablized;

    private boolean canaryStopStarted;
    private boolean canaryStopStabilized;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CallbackContextBuilder {
    }

    @JsonIgnore
    public void incrementRetryTimes() {
        stabilizationRetryTimes++;
    }


    private String retryKey;
    private int remainingRetryCount;

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
