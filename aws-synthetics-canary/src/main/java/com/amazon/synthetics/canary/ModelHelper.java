package com.amazon.synthetics.canary;

import com.amazonaws.arn.Arn;
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
    private static final String JS_SUFFIX = ".js";

    public static ResourceModel constructModel(Canary canary, ResourceModel model) {
        Map<String, String> tags = canary.tags();

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
        model.setVPCConfig(canary.vpcConfig() != null ? buildVpcConfigObject(canary.vpcConfig()) : null);
        model.setState(canary.status().stateAsString());
        model.setRunConfig(RunConfig.builder().timeoutInSeconds(
                canary.runConfig() != null ? canary.runConfig().timeoutInSeconds() : null)
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

    private static VPCConfig buildVpcConfigObject(final VpcConfigOutput vpcConfigOutput) {
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

    public static String buildCanaryArn(ResourceHandlerRequest<ResourceModel> request, String canaryName) {
        String accountId = request.getAwsAccountId();
        String region = request.getRegion();
        String resource = String.format("%s:%s", "canary", canaryName);
        String partition = getPartition(region);

        Arn arn = Arn.builder().withAccountId(accountId)
                .withPartition(partition)
                .withRegion(region)
                .withService("synthetics")
                .withResource(resource)
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

    private static String getPartition(String region) {
        String partition = "aws";
        if (region.contains("us-gov-")) {
            partition.concat("-us-gov");
        }
        if (region.contains("cn-")) {
            partition.concat("-cn");
        }
        return partition;
    }

    public static Map<String, Map<String, String>> updateTags(ResourceModel model, Map<String, String> existingTags) {
        System.out.println("Entering updateTags");
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

        if (copyExistingTags != null) {
            canaryTags = copyExistingTags.entrySet();
        }

        if (modelTagList == null) {
            return null;
        }
        Set<Map.Entry<String, String>> finalCanaryTags = canaryTags;

        System.out.println(("Model tags: " + modelTagsES));
        System.out.println(("ModelTagMap: " + modelTagMap));
        // Get an iterator
        Iterator modelIterator = modelTagsES.iterator();

        while (modelIterator.hasNext()) {
            Map.Entry modelEntry = (Map.Entry) modelIterator.next();
            System.out.println("modelEntry: " + modelEntry);
            if (finalCanaryTags.contains(modelEntry)) {
                System.out.println("finalCanaryTags: " + finalCanaryTags);
                System.out.println("modelEntry: " + modelEntry);
                modelIterator.remove();
            }
        }
        System.out.println(("tagMap after: " + modelTagMap));
        store.put("ADD_TAGS", modelTagMap);

        System.out.println("finalCanaryTags: " + modelTagsES);
        System.out.println(("copyExistingTags before: " + copyExistingTags));
        Iterator canaryTagIterator = finalCanaryTags.iterator();

        while (canaryTagIterator.hasNext()) {
            Map.Entry canaryEntry = (Map.Entry) canaryTagIterator.next();
            try {
                if (modelTagsCopyES.contains(canaryEntry)) {
                    canaryTagIterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(("copyExistingTags after: " + copyExistingTags));
        store.put("REMOVE_TAGS", copyExistingTags);


   /*     if ((filterModelTagPairs ||
                !filterModelTagPairs) && !modelTags.isEmpty()) {
            store.put("ADD_TAGS", tagMap);
        }

        Set<Map.Entry<String, String>> finalModelTags = modelTagsCopy;
        boolean filterCanaryTagPairs = canaryTags.removeIf(key -> key.getKey()
                .equals(finalModelTags.contains(new HashMap().put(key, key.getKey()))));

        if ((filterCanaryTagPairs ||
                !filterCanaryTagPairs) && !canaryTags.isEmpty()) {
            store.put("REMOVE_TAGS", copyExistingTags);
        }*/
        /*if (tagList == null || modelTags.containsAll(canaryTags)) {
            return null;
        }

        if (modelTags.removeAll(canaryTags) || !modelTags.removeAll(canaryTags) ) {
            // Tag these resources as these are additional
            store.put("ADD_TAGS", tagMap);
        }

        if (canaryTags.removeAll(modelTagsCopy) || !canaryTags.removeAll(modelTags)) {
            System.out.println("modelTags: check");
            // Tag these resources as these are additional
            System.out.println("modelTags: " + copyExistingTags);
            System.out.println("modelTags: " + copyExistingTags.values());
            store.put("REMOVE_TAGS", copyExistingTags);
        }
*/
        System.out.println("\nstore: " + store.values());
        return store;
    }

}





