package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigOutput;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.VpcConfigOutput;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;

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

    public static Canary getCanaryOrNull(AmazonWebServicesClientProxy proxy,
                                         SyntheticsClient syntheticsClient,
                                         String canaryName) {
        try {
            return getCanary(proxy, syntheticsClient, canaryName);
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }
    public static Canary getCanaryOrThrow(AmazonWebServicesClientProxy proxy,
                                          SyntheticsClient syntheticsClient,
                                          ResourceModel model) {
        return getCanaryOrThrow(proxy, syntheticsClient, model.getName());
    }
    public static Canary getCanaryOrThrow(AmazonWebServicesClientProxy proxy,
                                          SyntheticsClient syntheticsClient,
                                          String canaryName) {
        try {
            return getCanary(proxy, syntheticsClient, canaryName);
        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, canaryName, e);
        }
    }

    private static Canary getCanary(AmazonWebServicesClientProxy proxy,
                                    SyntheticsClient syntheticsClient,
                                    String canaryName) {
        GetCanaryResponse response = proxy.injectCredentialsAndInvokeV2(
            GetCanaryRequest.builder()
                .name(canaryName)
                .build(),
            syntheticsClient::getCanary);
        return response.canary();
    }
}
