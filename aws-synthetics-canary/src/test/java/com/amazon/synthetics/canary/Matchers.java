package com.amazon.synthetics.canary;


import software.amazon.awssdk.services.synthetics.model.Canary;

import static org.assertj.core.api.Assertions.assertThat;

public class Matchers {

    public static void assertThatModelsAreEqual(final Object rawModel,
                                                final Canary sdkModel) {
        assertThat(rawModel).isInstanceOf(ResourceModel.class);
        ResourceModel model = (ResourceModel)rawModel;
        assertThat(model.getName()).isEqualTo(sdkModel.name());
    }
}
