package com.amazon.synthetics.canary;

import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;


@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends TestBase {
    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private UpdateHandler handler;

    private ResourceHandlerRequest<ResourceModel> request;

    private ResourceModel model;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
        handler  = new UpdateHandler();
        request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-1.0", null))
                .clientRequestToken("clientRequestToken")
                .logicalResourceIdentifier("logicIdentifier")
                .build();
    }

    private ResourceModel buildModel(String runtimeVersion, Boolean isActiveTracing) {
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
        vpcConfig.setSubnetIds(subnetIds);
        vpcConfig.setSecurityGroupIds(securityGroups);

        Tag tagUpdate = Tag.builder().key("key2").value("value2").build();
        List<Tag> listTag = new ArrayList<>();
        listTag.add(tagUpdate);

        RunConfig runConfig = RunConfig.builder().timeoutInSeconds(600).memoryInMB(960).activeTracing(isActiveTracing).build();

        model = ResourceModel.builder()
                .name(String.format("canary_created_from_cloudformation-" + new DateTime().toString()))
                .artifactS3Location("s3://cloudformation-created-bucket")
                .code(codeObjectForTesting)
                .executionRoleArn("arn:aws:test::myaccount")
                .schedule(scheduleForTesting)
                .runtimeVersion(runtimeVersion)
                .startCanaryAfterCreation(true)
                .vPCConfig(vpcConfig)
                .tags(listTag)
                .runConfig(runConfig)
                .failureRetentionPeriod(31)
                .successRetentionPeriod(31)
                .build();

        return model;
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-1.0", null))
                .build();

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .schedule(canaryScheduleOutputForTesting())
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(true)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateRuntime(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-1.0")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getRuntimeVersion()).isEqualTo("syn-nodejs-2.0-beta");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateRuntimeDownVersion(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-1.0", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getRuntimeVersion()).isEqualTo("syn-1.0");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateDuration(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", null))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingToTrue(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", true))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingToFalse(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_updateActiveTracingFromTrueToFalse(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(buildModel("syn-nodejs-2.0-beta", false))
                .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
                .name("canarytestname")
                .executionRoleArn("test execution arn")
                .code(codeOutputObjectForTesting())
                .status(CanaryStatus.builder()
                        .state("RUNNING")
                        .build())
                .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).activeTracing(true).build())
                .schedule(canaryScheduleOutputWithNullDurationForTesting())
                .runtimeVersion("syn-nodejs-2.0-beta")
                .tags(tagExisting)
                .build();

        final CallbackContext callbackContext = CallbackContext.builder()
                .canaryUpdationStarted(false)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
                .canary(canary)
                .build();

        doReturn(getCanaryResponse)
                .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isEqualTo("3600");
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
                .canaryUpdationStarted(true)
                .canaryUpdationStablized(false)
                .canaryStartStarted(true)
                .canaryStartStablized(true)
                .canaryStopStarted(true)
                .canaryStopStabilized(true)
                .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_durationInSecondsNull() {
        ResourceModel model = buildModel("syn-nodejs-2.0-beta", true);
        model.getSchedule().setDurationInSeconds(null);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        Map<String, String> tagExisting = new HashMap<>();
        tagExisting.put("key2","value2");

        final Canary canary = Canary.builder()
            .name("canarytestname")
            .executionRoleArn("test execution arn")
            .code(codeOutputObjectForTesting())
            .status(CanaryStatus.builder()
                .state("RUNNING")
                .build())
            .runConfig(CanaryRunConfigOutput.builder().timeoutInSeconds(60).build())
            .schedule(canaryScheduleOutputWithNullDurationForTesting())
            .runtimeVersion("syn-nodejs-2.0-beta")
            .tags(tagExisting)
            .build();

        final CallbackContext callbackContext = CallbackContext.builder()
            .canaryUpdationStarted(false)
            .canaryUpdationStablized(false)
            .canaryStartStarted(true)
            .canaryStartStablized(true)
            .canaryStopStarted(true)
            .canaryStopStabilized(true)
            .build();

        final GetCanaryResponse getCanaryResponse = GetCanaryResponse.builder()
            .canary(canary)
            .build();

        doReturn(getCanaryResponse)
            .when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getResourceModel().getSchedule().getDurationInSeconds()).isNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);

        final CallbackContext callbackContextUpdated = CallbackContext.builder()
            .canaryUpdationStarted(true)
            .canaryUpdationStablized(false)
            .canaryStartStarted(true)
            .canaryStartStablized(true)
            .canaryStopStarted(true)
            .canaryStopStabilized(true)
            .build();
        assertThat(response.getCallbackContext()).isEqualTo(callbackContextUpdated);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModel().getRunConfig().getActiveTracing()).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}

