package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.synthetics.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import software.amazon.cloudformation.proxy.Logger;


public class ModelHelper {
    private static final String NODE_MODULES_DIR = "/nodejs/node_modules/";
    private static final String JS_SUFFIX = ".js";
    public static ResourceModel constructModel(Canary canary, ResourceModel model) {
        Map<String , String> tags = canary.tags();

        model.setId(canary.id());
        model.setName(canary.name());
        model.setArtifactS3Location(canary.artifactS3Location());
        model.setExecutionRoleArn(canary.executionRoleArn());
        model.setFailureRetentionPeriod(canary.failureRetentionPeriodInDays());
        model.setSuccessRetentionPeriod(canary.successRetentionPeriodInDays());
        model.setRuntimeVersion(canary.runtimeVersion());

        model.setCode(buildCodeObject(canary.code()));
        model.setSchedule(buildCanaryScheduleObject(canary.schedule()));
        // Tags are optional. Check for null
        model.setTags(tags != null ? buildTagObject(tags) : null);
        // VPC Config is optional. Check for null
        model.setVPCConfig(canary.vpcConfig() != null ? buildVpcConfigObject(canary.vpcConfig()): null);
        model.setState(canary.status().stateAsString());
        model.setRunConfig(RunConfig.builder().timeoutInSeconds(
                canary.runConfig() != null ? canary.runConfig().timeoutInSeconds() : null )
                .build());

        return model;
    }

    private static Code buildCodeObject(CanaryCodeOutput canaryCodeOutput) {
        Code code = Code.builder()
                .handler(canaryCodeOutput.handler())
                .build();
        return code;
    }

    private static Schedule buildCanaryScheduleObject(CanaryScheduleOutput canaryScheduleOutput) {
        Schedule schedule = Schedule.builder()
                .durationInSeconds(canaryScheduleOutput.durationInSeconds().toString())
                .expression(canaryScheduleOutput.expression()).build();
        return schedule;
    }

    private static List<Tag> buildTagObject(final Map<String, String> tags) {
        List<Tag> tagArrayList = new ArrayList<Tag>();
        if (tags == null) return null;
        tags.forEach((k, v) ->
                tagArrayList.add(Tag.builder().key(k).value(v).build()));
        return tagArrayList;
    }

    private static VPCConfig  buildVpcConfigObject(final VpcConfigOutput vpcConfigOutput) {
        List<String> subnetIds = vpcConfigOutput.subnetIds();
        List<String> securityGroupIds = vpcConfigOutput.securityGroupIds();

        return VPCConfig.builder()
                .subnetIds(subnetIds)
                .securityGroupIds(securityGroupIds)
                .vpcId(vpcConfigOutput.vpcId()).build();
    }

    public static SdkBytes compressRawScript(Code code) {
        // Handler name is in the format <function_name>.handler.
        // Need to strip out the .handler suffix

        String functionName = code.getHandler().split("\\.")[0];

        String jsFunctionName = functionName + JS_SUFFIX;
        String zipOutputFilePath = NODE_MODULES_DIR + jsFunctionName;
        String script = code.getScript();

        ByteArrayOutputStream byteArrayOutputStream = null;
        InputStream inputStream = null;
        ZipOutputStream zipByteOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            zipByteOutputStream = new ZipOutputStream(byteArrayOutputStream);
            inputStream = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));

            ZipEntry zipEntry = new ZipEntry(zipOutputFilePath);
            zipByteOutputStream.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int len;

            while ((len = inputStream.read(buffer)) > 0) {
                zipByteOutputStream.write(buffer, 0, len);
            }
            zipByteOutputStream.closeEntry();
            zipByteOutputStream.close();
            inputStream.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return SdkBytes.fromByteBuffer(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
    }
}





