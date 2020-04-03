package com.amazon.synthetics.canary;

import lombok.Builder;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import software.amazon.awssdk.services.synthetics.model.*;

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

    private boolean canaryDeleteStarted;
    private boolean canaryDeleteStabilized;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CallbackContextBuilder {
    }

    @JsonIgnore
    public void incrementRetryTimes() {
        stabilizationRetryTimes++;
    }

}
