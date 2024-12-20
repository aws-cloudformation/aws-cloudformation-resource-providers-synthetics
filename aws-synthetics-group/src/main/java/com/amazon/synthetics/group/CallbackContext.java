package com.amazon.synthetics.group;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@Builder
@lombok.EqualsAndHashCode(callSuper = true)
@Data
@JsonDeserialize(builder = CallbackContext.CallbackContextBuilder.class)
public class CallbackContext extends StdCallbackContext {
    private boolean groupCreationStarted;
    private boolean groupUpdateStarted;
    private boolean groupAssociationStarted;
    private List<String> addResourceList;
    private List<String> removeResourceList;
    private int addResourceListIndex;
    private int removeResourceListIndex;
    private boolean groupRemoveAssociationStarted;
    private int remainingRetryCount;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CallbackContextBuilder {
    }
}
