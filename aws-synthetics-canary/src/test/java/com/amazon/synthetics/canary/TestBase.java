package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestBase {

    public static ResourceModel buildModelForRead() {
        ResourceModel model = ResourceModel.builder()
                .name("cfncanary-unittest")
                .artifactS3Location("s3://cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                .code(codeObjectForTesting())
                .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                .schedule(scheduleObjectForTesting())
                .runtimeVersion("syn-1.0")
                .vPCConfig(vpcConfigForTesting())
                .state("RUNNING")
                .tags(buildTagObject(new HashMap<String, String>()))
                .runConfig(RunConfig.builder().timeoutInSeconds(50).build())
                .build();
        return model;
    }

    public static Code codeObjectForTesting() {
        Code codeObjectForTesting = new Code(null,
                null,
                null,
                null,
                "pageLoadBlueprint.handler");
        return codeObjectForTesting;
    }

    public static VPCConfig vpcConfigForTesting() {
        VPCConfig vpcConfig  = new VPCConfig();
        List<String> securityGroupIds = new ArrayList<String>();
        securityGroupIds.add("sg-085912345678492fb");
        securityGroupIds.add("sg-085912345678492fc");

        List<String> subnetIds = new ArrayList<String>();
        subnetIds.add("subnet-071f712345678e7c8");
        subnetIds.add("subnet-07fd123456788a036");

        vpcConfig.setSecurityGroupIds(securityGroupIds);
        vpcConfig.setSubnetIds(subnetIds);
        vpcConfig.setVpcId("vpc-2f09a348");

        return vpcConfig;
    }

    public static List<Tag> buildTagObject(final Map<String, String> tags) {
        List<Tag> tagArrayList = new ArrayList<Tag>();
        if (tags == null) return null;
        tags.forEach((k, v) ->
                tagArrayList.add(Tag.builder().key(k).value(v).build()));
        return tagArrayList;
    }

    /*
    Building model for testing
     */
    public static Schedule scheduleObjectForTesting() {
        Schedule scheduleObjectForTesting = new Schedule();
        scheduleObjectForTesting.setDurationInSeconds("3600");
        scheduleObjectForTesting.setExpression("rate(1 min)");
        return scheduleObjectForTesting;
    }

    /*
    **********************  Test Outputs ******************************
     */
    public static Canary canaryResponseObjectForTesting(String canaryName) {
        Canary cfnCanary = Canary.builder()
                .name(canaryName)
                .artifactS3Location("cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                .code(codeOutputObjectForTesting())
                .engineArn("arn:aws:us-east-1:123456789101:function:testFunction:1")
                .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .vpcConfig(canaryVpcConfigOutputForTesting())
                .status(canaryStatusForTesting())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(50).build())
                .build();
        return cfnCanary;
    }

    public static CanaryCodeOutput codeOutputObjectForTesting() {
        CanaryCodeOutput codeOutputObjectForTesting = CanaryCodeOutput.builder()
                .handler("pageLoadBlueprint.handler")
                .sourceLocationArn("arn:aws:lambda:us-west-2:440056434621:layer:cwsyn-cfncanary-017e8100-8bee-4ba9-bf6e-2e9837592425:1").build();
        return codeOutputObjectForTesting;
    }

    public static CanaryScheduleOutput canaryScheduleOutputForTesting() {
        CanaryScheduleOutput canaryScheduleOutput = CanaryScheduleOutput.builder()
                .expression("rate(1 min)")
                .durationInSeconds(Long.valueOf("3600")).build();
        return canaryScheduleOutput;
    }
    public static CanaryScheduleOutput canaryScheduleOutputWithNullDurationForTesting() {
        CanaryScheduleOutput canaryScheduleOutput = CanaryScheduleOutput.builder()
                .expression("rate(1 min)")
                .durationInSeconds(null).build();
        return canaryScheduleOutput;
    }

    public static CanaryStatus canaryStatusForTesting() {
        CanaryStatus canaryStatus = CanaryStatus.builder()
                .state("RUNNING")
                .stateReason("Running successfully")
                .stateReasonCode("200").build();
        return canaryStatus;
    }

    public static VpcConfigOutput canaryVpcConfigOutputForTesting() {
        VpcConfigOutput vpcConfigOutput = VpcConfigOutput.builder()
                .vpcId("vpc-2f09a348")
                .securityGroupIds("sg-085912345678492fb", "sg-085912345678492fc")
                .subnetIds("subnet-071f712345678e7c8", "subnet-07fd123456788a036").build();
        return vpcConfigOutput;
    }

    public static Map<String, String> sampleTags() {
        Map<String, String> tagMap = new HashMap<String, String>();
        tagMap.put("test1Key", "test1Value");
        tagMap.put("test2Key", "test12Value");
        return tagMap;
    }
}
