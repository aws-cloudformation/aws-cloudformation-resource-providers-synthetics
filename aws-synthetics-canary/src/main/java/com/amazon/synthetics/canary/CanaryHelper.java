package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigOutput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigOutput;

public class CanaryHelper {
    /**
     * Structured properties returned by the SDK will never be null, so we
     * need to check the structures own properties to determine whether it
     * is empty.
     * @param vpcConfigOutput
     * @return
     */
    public static boolean isNullOrEmpty(VpcConfigOutput vpcConfigOutput) {
        // Properties may be null during unit tests.
        if (vpcConfigOutput == null) {
            return true;
        }

        return !vpcConfigOutput.hasSubnetIds()
            && !vpcConfigOutput.hasSecurityGroupIds()
            && Strings.isNullOrEmpty(vpcConfigOutput.vpcId());
    }

    public static boolean isNullOrEmpty(CanaryRunConfigOutput runConfigOutput) {
        if (runConfigOutput == null) {
            return true;
        }

        return runConfigOutput.timeoutInSeconds() == null
            && runConfigOutput.memoryInMB() == null;
    }
}
