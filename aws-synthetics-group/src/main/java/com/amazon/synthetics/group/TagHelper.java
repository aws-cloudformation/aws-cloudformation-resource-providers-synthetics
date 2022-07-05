package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import com.amazonaws.arn.Arn;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class TagHelper {
    /**
     * convertToMap
     *
     * Converts a collection of Tag objects to a tag-name -> tag-value map.
     *
     * Note: Tag objects with null tag values will not be included in the output
     * map.
     *
     * @param tags Collection of tags to convert
     * @return Converted Map of tags
     */
    public static Map<String, String> convertToMap(final Collection<Tag> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return Collections.emptyMap();
        }
        return tags.stream()
            .filter(tag -> tag.getValue() != null)
            .collect(Collectors.toMap(
                Tag::getKey,
                Tag::getValue,
                (oldValue, newValue) -> newValue));
    }

    /**
     * convertToSet
     *
     * Converts a tag map to a set of Tag objects.
     *
     * Note: Like convertToMap, convertToSet filters out value-less tag entries.
     *
     * @param tagMap Map of tags to convert
     * @return Set of Tag objects
     */
    public static List<Tag> convertToList(final Map<String, String> tagMap) {
        if (MapUtils.isEmpty(tagMap)) {
            return Collections.emptyList();
        }
        return tagMap.entrySet().stream()
            .filter(tag -> tag.getValue() != null)
            .map(tag -> Tag.builder()
                .key(tag.getKey())
                .value(tag.getValue())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * generateTagsForCreate
     *
     * Generate tags to put into resource creation request.
     * This includes user defined tags and system tags as well.
     */
    public final Map<String, String> generateTagsForCreate(final ResourceModel resourceModel, final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        final Map<String, String> tagMap = new HashMap<>();

        // merge system tags with desired resource tags if your service supports CloudFormation system tags
        tagMap.putAll(handlerRequest.getSystemTags());

        if (handlerRequest.getDesiredResourceTags() != null) {
            tagMap.putAll(handlerRequest.getDesiredResourceTags());
        }

        // TODO: get tags from resource model based on your tag property name
        // TODO: tagMap.putAll(convertToMap(resourceModel.getTags()));
        return Collections.unmodifiableMap(tagMap);
    }

    /**
     * shouldUpdateTags
     *
     * Determines whether user defined tags have been changed during update.
     */
    public final boolean shouldUpdateTags(final ResourceModel resourceModel, final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        final Map<String, String> previousTags = getPreviouslyAttachedTags(handlerRequest);
        final Map<String, String> desiredTags = getNewDesiredTags(resourceModel, handlerRequest);
        return ObjectUtils.notEqual(previousTags, desiredTags);
    }

    /**
     * getPreviouslyAttachedTags
     *
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get previous attached user defined tags from both handlerRequest.getPreviousResourceTags (stack tags)
     * and handlerRequest.getPreviousResourceState (resource tags).
     */
    public Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        // get previous stack level tags from handlerRequest
        final Map<String, String> previousTags = handlerRequest.getPreviousResourceTags() != null ?
            handlerRequest.getPreviousResourceTags() : Collections.emptyMap();

        // TODO: get resource level tags from previous resource state based on your tag property name
        // TODO: previousTags.putAll(handlerRequest.getPreviousResourceState().getTags());
        return previousTags;
    }

    /**
     * getNewDesiredTags
     *
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get new user defined tags from both resource model and previous stack tags.
     */
    public Map<String, String> getNewDesiredTags(final ResourceModel resourceModel, final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        // get new stack level tags from handlerRequest
        final Map<String, String> desiredTags = handlerRequest.getDesiredResourceTags() != null ?
            handlerRequest.getDesiredResourceTags() : Collections.emptyMap();

        // TODO: get resource level tags from resource model based on your tag property name
        // TODO: desiredTags.putAll(convertToMap(resourceModel.getTags()));
        return desiredTags;
    }

    /**
     * Method to determine if the tags changed and return the tags to add and remove
     * @param model: To get the new tags
     * @param existingTags: Map of existing tags
     * @return Map of map of Tags to be added and removed
     */
    public static Map<String, Map<String, String>> updateTags(ResourceModel model, Map<String, String> existingTags) {
        Map<String, String> modelTagMap = new HashMap<>();
        List<Tag> modelTagList = model.getTags();
        Set<Map.Entry<String, String>> modelTagsES = null;
        Set<Map.Entry<String, String>> groupTags = null;
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

        groupTags = copyExistingTags.entrySet();

        if (modelTagList == null) {
            return null;
        }
        Set<Map.Entry<String, String>> finalGroupTags = groupTags;
        // Get an iterator
        Iterator<Map.Entry<String, String>> modelIterator = modelTagsES.iterator();
        while (modelIterator.hasNext()) {
            Map.Entry<String, String> modelEntry = modelIterator.next();
            if (finalGroupTags.contains(modelEntry)) {
                modelIterator.remove();
            }
        }
        // Store all the tags that need to be added to the group
        store.put(Constants.ADD_TAGS, modelTagMap);

        Iterator<Map.Entry<String, String>> groupTagIterator = finalGroupTags.iterator();
        while (groupTagIterator.hasNext()) {
            Map.Entry<String, String> canaryEntry = groupTagIterator.next();
            try {
                if (modelTagsCopyES.contains(canaryEntry)) {
                    groupTagIterator.remove();
                }
                if (canaryEntry.getKey().toString().startsWith("aws:")) {
                    groupTagIterator.remove();
                }
                if (!modelTagMap.isEmpty() && modelTagMap.containsKey(canaryEntry.getKey())) {
                    groupTagIterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Store all the tags that need to be removed from the group
        store.put(Constants.REMOVE_TAGS, copyExistingTags);
        return store;
    }

    /**
     * generateTagsToAdd
     *
     * Determines the tags the customer desired to define or redefine.
     */
    public Map<String, String> generateTagsToAdd(final Map<String, String> previousTags, final Map<String, String> desiredTags) {
        return desiredTags.entrySet().stream()
            .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects.equals(previousTags.get(e.getKey()), e.getValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue));
    }

    /**
     * getTagsToRemove
     *
     * Determines the tags the customer desired to remove from the function.
     */
    public Set<String> generateTagsToRemove(final Map<String, String> previousTags, final Map<String, String> desiredTags) {
        final Set<String> desiredTagNames = desiredTags.keySet();

        return previousTags.keySet().stream()
            .filter(tagName -> !desiredTagNames.contains(tagName))
            .collect(Collectors.toSet());
    }

    /**
     * generateTagsToAdd
     *
     * Determines the tags the customer desired to define or redefine.
     */
    public Set<Tag> generateTagsToAdd(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(desiredTags), new HashSet<>(previousTags));
    }

    /**
     * getTagsToRemove
     *
     * Determines the tags the customer desired to remove from the function.
     */
    public Set<Tag> generateTagsToRemove(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(previousTags), new HashSet<>(desiredTags));
    }


    /**
     * tagResource during update
     *
     * Calls the service:TagResource API.
     */
    private ProgressEvent<ResourceModel, CallbackContext>
    tagResource(final AmazonWebServicesClientProxy proxy, final ProxyClient<SdkClient> serviceClient, final ResourceModel resourceModel,
                final ResourceHandlerRequest<ResourceModel> handlerRequest, final CallbackContext callbackContext, final Map<String, String> addedTags, final Logger logger) {
        // TODO: add log for adding tags to resources during update
        // e.g. logger.log(String.format("[UPDATE][IN PROGRESS] Going to add tags for ... resource: %s with AccountId: %s",
        // resourceModel.getResourceName(), handlerRequest.getAwsAccountId()));

        // TODO: change untagResource in the method to your service API according to your SDK
        return proxy.initiate("AWS-Synthetics-Group::TagOps", serviceClient, resourceModel, callbackContext)
            .translateToServiceRequest(model ->
                Translator.tagResourceRequest(model, addedTags))
            .makeServiceCall((request, client) -> {
                return (AwsResponse) null;
                // TODO: replace the return null with your invoke log to call tagResource API to add tags
                // e.g. proxy.injectCredentialsAndInvokeV2(request, client.client()::tagResource))
            })
            .progress();
    }

    /**
     * untagResource during update
     *
     * Calls the service:UntagResource API.
     */
    private ProgressEvent<ResourceModel, CallbackContext>
    untagResource(final AmazonWebServicesClientProxy proxy, final ProxyClient<SdkClient> serviceClient, final ResourceModel resourceModel,
                  final ResourceHandlerRequest<ResourceModel> handlerRequest, final CallbackContext callbackContext, final Set<String> removedTags, final Logger logger) {
        // TODO: add log for removing tags from resources during update
        // e.g. logger.log(String.format("[UPDATE][IN PROGRESS] Going to remove tags for ... resource: %s with AccountId: %s",
        // resourceModel.getResourceName(), handlerRequest.getAwsAccountId()));

        // TODO: change untagResource in the method to your service API according to your SDK
        return proxy.initiate("AWS-Synthetics-Group::TagOps", serviceClient, resourceModel, callbackContext)
            .translateToServiceRequest(model ->
                Translator.untagResourceRequest(model, removedTags))
            .makeServiceCall((request, client) -> {
                return (AwsResponse) null;
                // TODO: replace the return null with your invoke log to call untag API to remove tags
                // e.g. proxy.injectCredentialsAndInvokeV2(request, client.client()::untagResource)
            })
            .progress();
    }

}
