package com.amazon.synthetics.canary;

import com.google.common.base.Strings;
import software.amazon.awssdk.services.synthetics.model.*;

import java.util.*;
import software.amazon.cloudformation.proxy.Logger;


public class ModelHelper {
    public static ResourceModel constructModel(Canary canary, ResourceModel model) {
        Map<String , String> tags = canary.tags();

        model.setId(canary.id());
        model.setName(canary.name());
        model.setArtifactLocation(canary.artifactLocation());
        model.setExecutionIAMRoleArn(canary.executionRoleArn());
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

    private static VpcConfig  buildVpcConfigObject(final VpcConfigOutput vpcConfigOutput) {
        List<String> subnetIds = vpcConfigOutput.subnetIds();
        List<String> securityGroupIds = vpcConfigOutput.securityGroupIds();

        return VpcConfig.builder()
                .subnetIds(subnetIds)
                .securityGroupIds(securityGroupIds)
                .vpcId(vpcConfigOutput.vpcId()).build();
    }
}





