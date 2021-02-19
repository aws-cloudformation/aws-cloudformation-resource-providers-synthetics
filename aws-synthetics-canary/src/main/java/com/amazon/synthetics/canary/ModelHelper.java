package com.amazon.synthetics.canary;

import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.CanaryCodeOutput;
import software.amazon.awssdk.services.synthetics.model.CanaryScheduleOutput;
import software.amazon.awssdk.services.synthetics.model.VpcConfigOutput;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModelHelper {
    private static final String NODE_MODULES_DIR = "/nodejs/node_modules/";
    private static final String PYTHON_DIR = "/python/";
    private static final String JS_SUFFIX = ".js";
    private static final String PY_SUFFIX = ".py";
    private static final String ADD_TAGS = "ADD_TAGS";
    private static final String REMOVE_TAGS = "REMOVE_TAGS";

    public static ResourceModel constructModel(Canary canary, ResourceModel model) {
        Map<String, String> tags = canary.tags();

        model.setId(canary.id());
        model.setName(canary.name());
        model.setArtifactS3Location("s3://" + canary.artifactS3Location());
        model.setExecutionRoleArn(canary.executionRoleArn());
        model.setFailureRetentionPeriod(canary.failureRetentionPeriodInDays());
        model.setSuccessRetentionPeriod(canary.successRetentionPeriodInDays());
        model.setRuntimeVersion(canary.runtimeVersion());
        model.setState(canary.status().stateAsString());

        model.setCode(buildCodeObject(canary.code()));
        model.setSchedule(buildCanaryScheduleObject(canary.schedule()));
        // Tags are optional. Check for null
        model.setTags(tags != null ? buildTagObject(tags) : null);

        // VPC Config is optional. Check for null
        if (!CanaryHelper.isNullOrEmpty(canary.vpcConfig())) {
            model.setVPCConfig(buildVpcConfigObject(canary.vpcConfig()));
        }

        if (!CanaryHelper.isNullOrEmpty(canary.runConfig())) {
            model.setRunConfig(RunConfig.builder()
                .timeoutInSeconds(canary.runConfig().timeoutInSeconds())
                .memoryInMB(canary.runConfig().memoryInMB())
                .activeTracing(canary.runConfig().activeTracing())
                .build());
        }

        return model;
    }

    private static Code buildCodeObject(CanaryCodeOutput canaryCodeOutput) {
        return Code.builder()
                .handler(canaryCodeOutput.handler())
                .build();
    }

    private static Schedule buildCanaryScheduleObject(CanaryScheduleOutput canaryScheduleOutput) {
        return Schedule.builder()
                .durationInSeconds(canaryScheduleOutput.durationInSeconds().toString())
                .expression(canaryScheduleOutput.expression()).build();
    }

    private static List<Tag> buildTagObject(final Map<String, String> tags) {
        List<Tag> tagArrayList = new ArrayList<Tag>();
        if (tags == null) return null;
        tags.forEach((k, v) ->
                tagArrayList.add(Tag.builder().key(k).value(v).build()));
        return tagArrayList;
    }

    private static VPCConfig buildVpcConfigObject(final VpcConfigOutput vpcConfigOutput) {
        List<String> subnetIds = vpcConfigOutput.subnetIds();
        List<String> securityGroupIds = vpcConfigOutput.securityGroupIds();

        return VPCConfig.builder()
                .subnetIds(subnetIds)
                .securityGroupIds(securityGroupIds)
                .vpcId(vpcConfigOutput.vpcId()).build();
    }

    public static SdkBytes compressRawScript(ResourceModel model) {
        // Handler name is in the format <function_name>.handler.
        // Need to strip out the .handler suffix

        String functionName = model.getCode().getHandler().split("\\.")[0];
        String runtimeLanguage = getRuntimeLanguage(model.getRuntimeVersion());
        String functionNameWithType = "";
        String zipOutputFilePath = "";

        /**
         Runtime is Node
         **/
        if ( runtimeLanguage.equalsIgnoreCase("nodejs")) {
            functionNameWithType = functionName + JS_SUFFIX;
            zipOutputFilePath = NODE_MODULES_DIR + functionNameWithType;
            System.out.println("zipOutputFilePath: " + zipOutputFilePath);
        }

        /**
         Runtime is Python
         **/
        if ( runtimeLanguage.equalsIgnoreCase("python")) {
            functionNameWithType = functionName + PY_SUFFIX;
            zipOutputFilePath = PYTHON_DIR + functionNameWithType;
        }

        String script = model.getCode().getScript();

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

    public static String buildCanaryArn(ResourceHandlerRequest<ResourceModel> request, String canaryName) {
        String accountId = request.getAwsAccountId();
        String region = request.getRegion();
        String resource = String.format("%s:%s", "canary", canaryName);
        String partition = request.getAwsPartition();

        Arn arn = Arn.builder().accountId(accountId)
                .partition(partition)
                .region(region)
                .service("synthetics")
                .resource(resource)
                .build();
        return arn.toString();
    }

    public static Map<String, String> buildTagInputMap(ResourceModel model) {
        Map<String, String> tagMap = new HashMap<>();
        List<Tag> tagList = model.getTags();
        // return null if no Tag specified.
        if (tagList == null) return null;

        for (Tag tag : tagList) {
            tagMap.put(tag.getKey(), tag.getValue());
        }
        return tagMap;
    }

    public static Map<String, Map<String, String>> updateTags(ResourceModel model, Map<String, String> existingTags) {
        Map<String, String> modelTagMap = new HashMap<>();
        List<Tag> modelTagList = model.getTags();
        Set<Map.Entry<String, String>> modelTagsES = null;
        Set<Map.Entry<String, String>> canaryTags = null;
        Set<Map.Entry<String, String>> modelTagsCopyES = null;
        Map<String, Map<String, String>> store = new HashMap<String, Map<String, String>>();
        Map<String, String> copyExistingTags = new HashMap<>(existingTags);

        if (modelTagList != null) {
            for (Tag tag : modelTagList) {
                modelTagMap.put(tag.getKey(), tag.getValue());
            }
            modelTagsES = modelTagMap.entrySet();
            modelTagsCopyES = new HashSet<Map.Entry<String, String>>(modelTagMap.entrySet());
        }

        canaryTags = copyExistingTags.entrySet();

        if (modelTagList == null) {
            return null;
        }
        Set<Map.Entry<String, String>> finalCanaryTags = canaryTags;
        // Get an iterator
        Iterator<Map.Entry<String, String>> modelIterator = modelTagsES.iterator();
        while (modelIterator.hasNext()) {
            Map.Entry<String, String> modelEntry = modelIterator.next();
            if (finalCanaryTags.contains(modelEntry)) {
                modelIterator.remove();
            }
        }
        // Store all the tags that need to be added to the canary
        store.put(ADD_TAGS, modelTagMap);

        Iterator<Map.Entry<String, String>> canaryTagIterator = finalCanaryTags.iterator();
        while (canaryTagIterator.hasNext()) {
            Map.Entry<String, String> canaryEntry = canaryTagIterator.next();
            try {
                if (modelTagsCopyES.contains(canaryEntry)) {
                    canaryTagIterator.remove();
                }
                if (canaryEntry.getKey().toString().startsWith("aws:")) {
                    canaryTagIterator.remove();
                }
                if (!modelTagMap.isEmpty() && modelTagMap.containsKey(canaryEntry.getKey())) {
                    canaryTagIterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Store all the tags that need to be removed to the canary
        store.put(REMOVE_TAGS, copyExistingTags);
        return store;
    }

    public static boolean isNullOrEmpty(VPCConfig vpcConfig) {
        return vpcConfig == null
            || vpcConfig.getSubnetIds() == null
            || vpcConfig.getSubnetIds().isEmpty()
            || vpcConfig.getSecurityGroupIds() == null
            || vpcConfig.getSecurityGroupIds().isEmpty();
    }

    private static String getRuntimeLanguage(String runtimeVersion){
        String language="";
        switch (runtimeVersion) {
            case "syn-python-selenium-1.0":
                language = "python";
                break;
            default:
                language = "nodejs";
        }
        return language;
    }
}





