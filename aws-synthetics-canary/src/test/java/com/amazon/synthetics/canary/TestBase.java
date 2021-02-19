package com.amazon.synthetics.canary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.synthetics.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class TestBase {
    protected static final String CANARY_NAME = "canary-name";
    protected static final String ERROR_STATE_REASON = "Failure message";

    protected static final ResourceHandlerRequest<ResourceModel> REQUEST = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, false, true))
        .awsPartition("aws")
        .region("us-west-2")
        .build();
    protected static final ResourceHandlerRequest<ResourceModel> REQUEST_START_CANARY = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, true, true))
        .awsPartition("aws")
        .region("us-west-2")
        .build();

    protected AmazonWebServicesClientProxy proxy = mock(AmazonWebServicesClientProxy.class);
    protected Logger logger = new ConsoleLogger();

    private static class ConsoleLogger implements Logger {
        @Override
        public void log(String s) {
            System.out.println("\u001B[36m" + s + "\u001B[m");
        }
    }

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

    protected void configureGetCanaryResponse(CanaryState state) {
        configureGetCanaryResponse(state, null);
    }

    protected void configureGetCanaryResponse(CanaryState state, String stateReason) {
        configureGetCanaryResponse(createCanaryWithState(state, stateReason));
    }

    protected void configureGetCanaryResponse(Canary canary) {
        when(proxy.injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(canary.name()).build()), any()))
            .thenReturn(GetCanaryResponse.builder().canary(canary).build());
    }

    protected void configureGetCanaryResponse(Throwable throwable) {
        when(proxy.injectCredentialsAndInvokeV2(eq(GetCanaryRequest.builder().name(CANARY_NAME).build()), any()))
            .thenThrow(throwable);
    }

    protected static Canary createCanaryWithState(CanaryState state, String stateReason) {
        return Canary.builder()
            .name(CANARY_NAME)
            .executionRoleArn("test execution arn")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state(state)
                .stateReason(stateReason)
                .build())
            .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
            .schedule(canaryScheduleOutputForTesting())
            .runtimeVersion("syn-1.0")
            .build();
    }

    protected static ResourceModel buildModel() {
        return buildModel("syn-1.0", null, true, true);
    }

    protected static ResourceModel buildModel(boolean useOptionalValues) {
        return buildModel("syn-1.0", null, true, false);
    }

    protected static ResourceModel buildModel(String runtimeVersion, Boolean isActiveTracing) {
        return buildModel(runtimeVersion, isActiveTracing, true, true);
    }
    protected static ResourceModel buildModel(String runtimeVersion, Boolean isActiveTracing, Boolean startCanaryAfterCreation, boolean useOptionalValues) {
        final Code codeObjectForTesting = new Code(null,
            null,
            null,
            "var synthetics = require('Synthetics');\n" +
                "const log = require('SyntheticsLogger');\n" +
                "\n" +
                "const pageLoadBlueprint = async function () {\n" +
                "\n" +
                "    // INSERT URL here\n" +
                "    const URL = \"https://amazon.com\";\n" +
                "\n" +
                "    let page = await synthetics.getPage();\n" +
                "    const response = await page.goto(URL, {waitUntil: 'domcontentloaded', timeout: 30000});\n" +
                "    //Wait for page to render.\n" +
                "    //Increase or decrease wait time based on endpoint being monitored.\n" +
                "    await page.waitFor(15000);\n" +
                "    await synthetics.takeScreenshot('loaded', 'loaded');\n" +
                "    let pageTitle = await page.title();\n" +
                "    log.info('Page title: ' + pageTitle);\n" +
                "    if (response.status() !== 200) {\n" +
                "        throw \"Failed to load page!\";\n" +
                "    }\n" +
                "};\n" +
                "\n" +
                "exports.handler = async () => {\n" +
                "    return await pageLoadBlueprint();\n" +
                "};",
            "pageLoadBlueprint.handler");

        final Schedule scheduleForTesting = new Schedule();
        scheduleForTesting.setDurationInSeconds("3600");
        scheduleForTesting.setExpression("rate(1 min)");

        ArrayList<String> subnetIds = new ArrayList<>();
        subnetIds.add("subnet-3a473011");
        subnetIds.add("subnet-123f3159");

        ArrayList<String> securityGroups = new ArrayList<>();
        securityGroups.add("sg-5582b213");

        final VPCConfig vpcConfig = new VPCConfig();
        Tag tagUpdate;
        RunConfig runConfig;
        List<Tag> listTag = new ArrayList<>();

        if (useOptionalValues) {
            vpcConfig.setSubnetIds(subnetIds);
            vpcConfig.setSecurityGroupIds(securityGroups);

            tagUpdate = Tag.builder().key("key2").value("value2").build();
            listTag.add(tagUpdate);

            runConfig = RunConfig.builder().timeoutInSeconds(600).memoryInMB(960).activeTracing(isActiveTracing).build();

            return ResourceModel.builder()
                    .name(CANARY_NAME)
                    .artifactS3Location("s3://cloudformation-created-bucket")
                    .code(codeObjectForTesting)
                    .executionRoleArn("arn:aws:test::myaccount")
                    .schedule(scheduleForTesting)
                    .runtimeVersion(runtimeVersion)
                    .startCanaryAfterCreation(startCanaryAfterCreation)
                    .vPCConfig(vpcConfig)
                    .tags(listTag)
                    .runConfig(runConfig)
                    .failureRetentionPeriod(31)
                    .successRetentionPeriod(31)
                    .build();
        }

        return ResourceModel.builder()
            .name(CANARY_NAME)
            .artifactS3Location("s3://cloudformation-created-bucket")
            .code(codeObjectForTesting)
            .executionRoleArn("arn:aws:test::myaccount")
            .schedule(scheduleForTesting)
            .runtimeVersion(runtimeVersion)
            .startCanaryAfterCreation(startCanaryAfterCreation)
            .build();
    }
}
