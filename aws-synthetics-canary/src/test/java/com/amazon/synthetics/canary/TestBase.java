package com.amazon.synthetics.canary;

import com.google.common.base.Strings;

import software.amazon.awssdk.services.lambda.model.ListTagsRequest;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.synthetics.model.ArtifactConfigOutput;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeInput;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeOutput;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigInput;
import software.amazon.awssdk.services.synthetics.model.CanaryRunConfigOutput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleInput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleOutput;
import software.amazon.awssdk.services.synthetics.model.CanaryState;
import software.amazon.awssdk.services.synthetics.model.CanaryStateReasonCode;
import software.amazon.awssdk.services.synthetics.model.CanaryStatus;
import software.amazon.awssdk.services.synthetics.model.CreateCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryRequest;
import software.amazon.awssdk.services.synthetics.model.GetCanaryResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceToTag;
import software.amazon.awssdk.services.synthetics.model.S3EncryptionConfig;
import software.amazon.awssdk.services.synthetics.model.VpcConfigInput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigOutput;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestBase {
    protected static final String CANARY_NAME = "canary-name";
    protected static final String ERROR_STATE_REASON = "Failure message";
    protected static final String MISSING_TAGGING_PERMISSIONS_ERROR_MESSAGE = "User: arn:aws:sts::123456789012:assumed-role/MissingTaggingPermissionsRole/AWSCloudFormation " +
        "is not authorized to perform: synthetics:UntagResource on resource: arn:aws:synthetics:us-west-2:123456789012:canary:canary-name " +
        "with an explicit deny";

    protected static final ResourceHandlerRequest<ResourceModel> REQUEST_WITH_DELETELAMBDA = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, false, true, true))
        .awsPartition("aws")
        .region("us-west-2")
        .build();

    protected static final ResourceHandlerRequest<ResourceModel> REQUEST_WITH_DELETELAMBDA_FALSE = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, false, true, false))
            .awsPartition("aws")
            .region("us-west-2")
            .build();

    protected static final ResourceHandlerRequest<ResourceModel> REQUEST = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, false, true, null))
        .awsPartition("aws")
        .region("us-west-2")
        .build();

    protected static final ResourceHandlerRequest<ResourceModel> REQUEST_NULL_START_CANARY = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, null, true, null))
            .awsPartition("aws")
            .region("us-west-2")
            .build();
    protected static final ResourceHandlerRequest<ResourceModel> REQUEST_START_CANARY = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false, true, true, null))
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

    public static ResourceModel buildModelForRead(boolean includeOptionals, boolean useDefaultS3Encryption) {
        if (includeOptionals) {
            ResourceModel model = ResourceModel.builder()
                    .name(CANARY_NAME)
                    .id("test-id")
                    .artifactS3Location("s3://cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                    .code(codeObjectForTesting())
                    .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                    .schedule(scheduleObjectForTesting())
                    .runtimeVersion("syn-1.0")
                    .vPCConfig(vpcConfigForTesting())
                    .state("RUNNING")
                    .tags(buildTagObject(new HashMap<String, String>()))
                    .runConfig(RunConfig.builder().timeoutInSeconds(50).memoryInMB(960).activeTracing(true).build())
                    .successRetentionPeriod(31)
                    .failureRetentionPeriod(31)
                    .artifactConfig(ArtifactConfig.builder()
                            .s3Encryption(S3Encryption.builder()
                                    .encryptionMode("SSE")
                                    .kmsKeyArn(useDefaultS3Encryption ? null : "arn:kms")
                                    .build())
                            .build())
                    .resourcesToReplicateTags(Collections.singletonList(ResourceToTag.LAMBDA_FUNCTION.toString()))
                    .build();
            return model;
        }
        ResourceModel model = ResourceModel.builder()
                .name(CANARY_NAME)
                .id("test-id")
                .artifactS3Location("s3://cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                .code(codeObjectForTesting())
                .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                .schedule(scheduleObjectForTesting())
                .runtimeVersion("syn-1.0")
                .state("RUNNING")
                .tags(buildTagObject(new HashMap<String, String>()))
                .build();
        return model;
    }

    public static Code codeObjectForTesting() {
        Code codeObjectForTesting = new Code(null,
                null,
                null,
                null,
                "pageLoadBlueprint.handler",
                "arn:aws:lambda:us-west-2:440056434621:layer:cwsyn-cfncanary-017e8100-8bee-4ba9-bf6e-2e9837592425:1");
        return codeObjectForTesting;
    }

    public static VPCConfig vpcConfigForTesting() {
        VPCConfig vpcConfig = new VPCConfig();
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
    public static Canary canaryResponseObjectForTesting(String canaryName, boolean includeOptionals, boolean useDefaultS3Encryption) {
        if (includeOptionals) {
            Canary cfnCanary = Canary.builder()
                    .name(canaryName)
                    .id("test-id")
                    .artifactS3Location("cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                    .code(codeOutputObjectForTesting())
                    .engineArn("arn:aws:us-east-1:123456789101:function:testFunction:1")
                    .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                    .schedule(canaryScheduleOutputForTesting())
                    .runtimeVersion("syn-1.0")
                    .vpcConfig(canaryVpcConfigOutputForTesting())
                    .status(canaryStatusForTesting())
                    .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(50).memoryInMB(960).activeTracing(true).build())
                    .artifactConfig(ArtifactConfigOutput.builder()
                            .s3Encryption(S3EncryptionConfig.builder()
                                    .encryptionMode("SSE")
                                    .kmsKeyArn(useDefaultS3Encryption ? null : "arn:kms")
                                    .build())
                            .build())
                    .successRetentionPeriodInDays(31)
                    .failureRetentionPeriodInDays(31)
                    .build();
            return cfnCanary;
        }
        Canary cfnCanary = Canary.builder()
                .name(canaryName)
                .id("test-id")
                .artifactS3Location("cw-syn-results-440056434621-us-west-2/canary/canary-1254-38b-0a58c26fe372")
                .code(codeOutputObjectForTesting())
                .engineArn("arn:aws:us-east-1:123456789101:function:testFunction:1")
                .executionRoleArn("arn:aws:us-east-1:1234567891000:role/SyntheticsRole")
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .status(canaryStatusForTesting())
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
        return createCanaryWithState(state, stateReason, null);
    }

    protected static Canary createCanaryWithState(CanaryState state, String stateReason, CanaryStateReasonCode stateReasonCode) {
        return Canary.builder()
                .name(CANARY_NAME)
                .executionRoleArn("test execution arn")
                .engineArn("test:lambda:arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state(state)
                        .stateReason(stateReason)
                        .stateReasonCode(stateReasonCode)
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .build();
    }

    protected void configureLambdaListTagsResponse() {
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tags(new HashMap<>())
                .build();
        doReturn(listTagsResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(ListTagsRequest.class), any());
    }

    protected static ResourceModel buildModel() {
        return buildModel("syn-1.0", null, true, true, null);
    }

    protected static ResourceModel buildModel(boolean useOptionalValues) {
        return buildModel("syn-1.0", null, true, useOptionalValues, null);
    }

    protected static ResourceModel buildModel(String runtimeVersion, Boolean isActiveTracing) {
        return buildModel(runtimeVersion, isActiveTracing, true, true, null);
    }

    protected static ResourceModel buildModel(String runtimeVersion, Boolean isActiveTracing, Boolean startCanaryAfterCreation, boolean useOptionalValues, Boolean deleteLambdaResources) {

        TestBase tb = new TestBase();


        tb.logger.log("resource model building with: " + runtimeVersion + " " + isActiveTracing + " " + startCanaryAfterCreation + " " + useOptionalValues);


        final Code codeObjectForNodeJS = new Code(null,
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
            "pageLoadBlueprint.handler",
                null);

        final Code codeObjectForPython = new Code(null,
            null,
            null,
            "from aws_synthetics.selenium import synthetics_webdriver as syn_webdriver\nfrom aws_synthetics.common import synthetics_logger as logger\n\ndef main():\n  url = \"https://etsy.com\"\n\n  # Set screenshot option\n  takeScreenshot = True\n\n  browser = syn_webdriver.Chrome()\n  browser.get(url)\n\n  if takeScreenshot:\n    browser.save_screenshot(\"loaded.png\")\n\n  response_code = syn_webdriver.get_http_response(url)\n  if not response_code or response_code < 200 or response_code > 299:\n    raise Exception(\"Failed to load page!\")\n  logger.info(\"Canary successfully executed\")\n\ndef handler(event, context):\n  # user defined log statements using synthetics_logger\n  logger.info(\"Selenium Python heartbeat canary\")\n  return main()\n",
            "pageLoadBlueprint.handler",
                null);

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

        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put("key1", "value1");


        List<BaseScreenshot> baseScreenshots = new ArrayList<>();
        baseScreenshots.add(BaseScreenshot.builder()
                .screenshotName("test.png")
                .ignoreCoordinates(new ArrayList<>())
                .build());
        final VisualReference visualReference = new VisualReference();
        visualReference.setBaseCanaryRunId("95e5cbe3-44ab-4a75-b886-ad2be207d899");
        visualReference.setBaseScreenshots(baseScreenshots);

        final ArtifactConfig artifactConfig = new ArtifactConfig();
        artifactConfig.setS3Encryption(S3Encryption.builder()
                .encryptionMode("SSE")
                .kmsKeyArn("mock_kms_arn")
                .build());

        if (useOptionalValues) {
            vpcConfig.setSubnetIds(subnetIds);
            vpcConfig.setSecurityGroupIds(securityGroups);

            tagUpdate = Tag.builder().key("key2").value("value2").build();
            listTag.add(tagUpdate);

            runConfig = RunConfig.builder()
                    .timeoutInSeconds(600)
                    .memoryInMB(960)
                    .activeTracing(isActiveTracing)
                    .environmentVariables(environmentVariables)
                    .build();

            return ResourceModel.builder()
                    .name(CANARY_NAME)
                    .artifactS3Location("s3://cloudformation-created-bucket")
                    .code(Pattern.compile("^syn-python-*").matcher(runtimeVersion).matches() ? codeObjectForPython : codeObjectForNodeJS)
                    .executionRoleArn("arn:aws:test::myaccount")
                    .schedule(scheduleForTesting)
                    .runtimeVersion(runtimeVersion)
                    .startCanaryAfterCreation(startCanaryAfterCreation)
                    .deleteLambdaResourcesOnCanaryDeletion(deleteLambdaResources)
                    .vPCConfig(vpcConfig)
                    .visualReference(visualReference)
                    .artifactConfig(artifactConfig)
                    .tags(listTag)
                    .runConfig(runConfig)
                    .failureRetentionPeriod(31)
                    .successRetentionPeriod(31)
                    .resourcesToReplicateTags(Collections.singletonList(ResourceToTag.LAMBDA_FUNCTION.toString()))
                    .build();
        }

        return ResourceModel.builder()
                .name(CANARY_NAME)
                .artifactS3Location("s3://cloudformation-created-bucket")
                .code(Pattern.compile("^syn-python-.*").matcher(runtimeVersion).matches() ? codeObjectForPython : codeObjectForNodeJS)
                .executionRoleArn("arn:aws:test::myaccount")
                .schedule(scheduleForTesting)
                .runtimeVersion(runtimeVersion)
                .startCanaryAfterCreation(startCanaryAfterCreation)
                .build();
    }

    protected static CreateCanaryRequest buildCreateCanaryRequest(boolean includeOptionalFields, ResourceModel model) {
        final CanaryCodeInput canaryCodeInput = CanaryCodeInput.builder()
                .handler(model.getCode().getHandler())
                .s3Bucket(model.getCode().getS3Bucket())
                .s3Key(model.getCode().getS3Key())
                .s3Version(model.getCode().getS3ObjectVersion())
                .zipFile(model.getCode().getScript() != null ? ModelHelper.compressRawScript(model) : null)
                .build();
        Long durationInSeconds = !Strings.isNullOrEmpty(model.getSchedule().getDurationInSeconds()) ?
                Long.valueOf(model.getSchedule().getDurationInSeconds()) : null;
        final CanaryScheduleInput canaryScheduleInput = CanaryScheduleInput.builder()
                .expression(model.getSchedule().getExpression())
                .durationInSeconds(durationInSeconds)
                .build();

        if (includeOptionalFields) {
            CanaryRunConfigInput canaryRunConfigInput = CanaryRunConfigInput.builder()
                    .timeoutInSeconds(model.getRunConfig().getTimeoutInSeconds())
                    .memoryInMB(960)
                    .activeTracing(Boolean.TRUE.equals(model.getRunConfig().getActiveTracing()))
                    .environmentVariables(model.getRunConfig().getEnvironmentVariables())
                    .build();

            VpcConfigInput vpcConfigInput = null;
            if (model.getVPCConfig() != null) {
                vpcConfigInput = VpcConfigInput.builder()
                        .subnetIds(model.getVPCConfig().getSubnetIds())
                        .securityGroupIds(model.getVPCConfig().getSecurityGroupIds())
                        .build();
            }
            return CreateCanaryRequest.builder()
                    .name(model.getName())
                    .executionRoleArn(model.getExecutionRoleArn())
                    .schedule(canaryScheduleInput)
                    .artifactS3Location(model.getArtifactS3Location())
                    .runtimeVersion(model.getRuntimeVersion())
                    .code(canaryCodeInput)
                    .tags(ModelHelper.buildTagInputMap(model))
                    .vpcConfig(vpcConfigInput)
                    .failureRetentionPeriodInDays(model.getFailureRetentionPeriod())
                    .successRetentionPeriodInDays(model.getSuccessRetentionPeriod())
                    .runConfig(canaryRunConfigInput)
                    .artifactConfig(ModelHelper.getArtifactConfigInput(model.getArtifactConfig()))
                    .resourcesToReplicateTags(ModelHelper.buildReplicateTags(model.getResourcesToReplicateTags()))
                    .build();
        }
        return CreateCanaryRequest.builder()
                .name(model.getName())
                .executionRoleArn(model.getExecutionRoleArn())
                .schedule(canaryScheduleInput)
                .artifactS3Location(model.getArtifactS3Location())
                .runtimeVersion(model.getRuntimeVersion())
                .code(canaryCodeInput)
                .artifactConfig(ModelHelper.getArtifactConfigInput(model.getArtifactConfig()))
                .build();
    }

    public ResourceHandlerRequest<ResourceModel> buildResourceHandlerRequestWithName(String canaryName) {
        final ResourceModel model = ResourceModel.builder().name(canaryName).build();

        return ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
    }

    public ResourceHandlerRequest<ResourceModel> buildResourceHandlerRequestWithTagReplication(String canaryName) {
        final ResourceModel model = ResourceModel.builder()
                .name(canaryName)
                .resourcesToReplicateTags(Collections.singletonList(ResourceToTag.LAMBDA_FUNCTION.toString()))
                .build();
 
        return ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
    }
}
