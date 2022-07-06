package com.amazon.synthetics.group;

import java.util.ArrayList;
import software.amazon.awssdk.awscore.AwsRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.services.synthetics.model.CreateGroupRequest;
import software.amazon.awssdk.services.synthetics.model.DeleteGroupRequest;
import software.amazon.awssdk.services.synthetics.model.GetGroupRequest;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.ListGroupsRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupsResponse;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   * @param model resource model
   * @return createGroupRequest the aws create group request to create a resource
   */
  static CreateGroupRequest translateToCreateRequest(final ResourceModel model) {
    final CreateGroupRequest createGroupRequest = CreateGroupRequest.builder()
        .name(model.getName())
        .tags(TagHelper.convertToMap(model.getTags()))
        .build();

    return createGroupRequest;
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return getGroupRequest the aws get group request to describe a resource
   */
  static GetGroupRequest translateToReadRequest(final ResourceModel model) {
    final GetGroupRequest getGroupRequest = GetGroupRequest.builder()
        .groupIdentifier(model.getName())
        .build();
    return getGroupRequest;
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param group the Synthetics read group response
   * @param relatedResources List of related resources
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final Group group, List<String> relatedResources) {
    return ResourceModel.builder()
        .name(group.name())
        .id(group.id())
        .tags(TagHelper.convertToList(group.tags()))
        .resourceArns(relatedResources)
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return deleteGroupRequest the aws delete group request to delete a resource
   */
  static DeleteGroupRequest translateToDeleteRequest(final ResourceModel model) {
    final DeleteGroupRequest deleteGroupRequest = DeleteGroupRequest.builder()
        .groupIdentifier(model.getName())
        .build();
    return deleteGroupRequest;
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return listGroupsRequest the aws list group request to list resources within aws account
   */
  static ListGroupsRequest translateToListRequest(final String nextToken) {
    final ListGroupsRequest listGroupsRequest = ListGroupsRequest.builder()
        .nextToken(nextToken)
        .build();
    return listGroupsRequest;
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param listGroupsResponse the aws cloudwatch Synthetics list groups response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final ListGroupsResponse listGroupsResponse) {
    if (listGroupsResponse == null) {
      return new ArrayList<>();
    }
    return streamOfOrEmpty(listGroupsResponse.groups())
        .map(resource -> ResourceModel.builder()
            .name(resource.name())
            .id(resource.id())
            .build())
        .collect(Collectors.toList());
  }


  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  /**
   * Request to add tags to a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static AwsRequest tagResourceRequest(final ResourceModel model, final Map<String, String> addedTags) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L39-L43
    return awsRequest;
  }

  /**
   * Request to add tags to a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static AwsRequest untagResourceRequest(final ResourceModel model, final Set<String> removedTags) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L39-L43
    return awsRequest;
  }
}
