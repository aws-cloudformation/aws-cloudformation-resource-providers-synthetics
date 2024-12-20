package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import com.amazonaws.arn.Arn;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.AssociateResourceRequest;
import software.amazon.awssdk.services.synthetics.model.DisassociateResourceRequest;
import software.amazon.awssdk.services.synthetics.model.GetGroupRequest;
import software.amazon.awssdk.services.synthetics.model.GetGroupResponse;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.ListGroupResourcesRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupResourcesResponse;
import software.amazon.awssdk.services.synthetics.model.ResourceNotFoundException;
import software.amazon.awssdk.services.synthetics.model.ValidationException;
import software.amazon.awssdk.regions.Region;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


/**
 * Base class for the functionality that could be shared across Create/Read/Update/Delete/List Handlers
  */
public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
  private final Action action;

  public BaseHandlerStd(Action action) {
    this.action = action;
  }

  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final Logger logger
  ) {
    Region region = request.getRegion() != null ? Region.of(request.getRegion()) : Region.US_WEST_2;
    Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap = ClientBuilder.getClientMap(proxy);
    ProxyClient<SyntheticsClient> proxyClient = proxyClientMap.get(region);
    return handleRequest(
        proxy, 
        request, 
        callbackContext != null ? callbackContext : CallbackContext.builder().build(), 
        proxyClientMap,
        proxyClient, 
        logger);
  }

  /**
   * This handleRequest with a proxy client is required to run unit tests
   * @param proxy
   * @param request
   * @param callbackContext
   * @param proxyClientMap
   * @param logger
   * @return
   */

  /**
   * Overridden in every handler based on the action
   * @return
   */
  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
      final ProxyClient<SyntheticsClient> proxyClient,
      final Logger logger
  );

  /**
   * Wrapper to call getGroup with Synthetics client and handle
   * the response/ error
   * @return Group
   */
  protected Group getGroupOrThrow(
        AmazonWebServicesClientProxy proxy, 
        ProxyClient<SyntheticsClient> proxyClient,
        ResourceModel model,
        Logger logger) {
    try {
      logger.log(Constants.GET_GROUP_CALL);
      GetGroupRequest getGroupRequest = com.amazon.synthetics.group.Translator.translateToReadRequest(model);
      GetGroupResponse getGroupResponse = proxy.injectCredentialsAndInvokeV2(getGroupRequest,
          proxyClient.client()::getGroup);
      return getGroupResponse.group();
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, model.getName(), e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  /**
   * Wrapper to call getGroupResources api with Synthetics client and handle the response/error
   * @return List</String>: List of resource arns associated with the group
   */
  protected List<String> getGroupResourcesOrThrow(
      AmazonWebServicesClientProxy proxy, 
      ProxyClient<SyntheticsClient> proxyClient,
      ResourceModel model,
      Logger logger) {
    try {
      logger.log(Constants.LIST_GROUP_RESOURCES_CALL);
      ListGroupResourcesRequest listGroupResourcesRequest = ListGroupResourcesRequest.builder()
          .groupIdentifier(model.getName())
          .build();
      ListGroupResourcesResponse listGroupResourcesResponse = proxy.injectCredentialsAndInvokeV2(listGroupResourcesRequest,
          proxyClient.client()::listGroupResources);
      return listGroupResourcesResponse.resources();
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, model.getName(), e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  /**
   * Wrapper around associateResource call to Synthetics client and handle the response/error
   * @param canaryArn: ResourceArn
   */
  protected void addAssociatedResource(
        String canaryArn, 
        AmazonWebServicesClientProxy proxy, 
        Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
        ResourceModel model,
        Logger logger) {
    // Translate resource model to create group request
    // call associate resource request
    logger.log(Constants.MAKING_ADD_ASSOCIATE);
    try {
      Arn resourceArn = Arn.fromString(canaryArn);
      AssociateResourceRequest associateResourceRequest = AssociateResourceRequest.builder()
          .resourceArn(canaryArn)
          .groupIdentifier(model.getName())
          .build();
      proxy.injectCredentialsAndInvokeV2(associateResourceRequest,
          proxyClientMap.get(Region.of(resourceArn.getRegion())).client()::associateResource);
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, canaryArn, e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  /**
   * Wrapper around associateResource call to Synthetics client and handle the response/error
   * @param canaryArn: ResourceArn
   */
  protected void removeAssociatedResource(
        String canaryArn, 
        AmazonWebServicesClientProxy proxy, 
        Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
        ResourceModel model,
        Logger logger) {
    logger.log(Constants.MAKING_REMOVE_ASSOCIATE);
    try {
      Arn resourceArn = Arn.fromString(canaryArn);
      DisassociateResourceRequest disassociateResourceRequest = DisassociateResourceRequest.builder()
          .groupIdentifier(model.getName())
          .resourceArn(canaryArn)
          .build();
      proxy.injectCredentialsAndInvokeV2(disassociateResourceRequest, proxyClientMap.get(Region.of(resourceArn.getRegion())).client()::disassociateResource);
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, canaryArn, e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  /**
   * Function to add AssociatedResources list. It determines which resource will be added in one round based on
   * AddResourceListIndex in the callbackContext
   * @param useResourceDiffList: boolean to indicate which list should be used to add (for update request this is true,
   *                           for create this is false
   * @return send back an in progress event
   */
  protected ProgressEvent<ResourceModel, CallbackContext> addAssociatedResources(
      boolean useResourceDiffList, 
      AmazonWebServicesClientProxy proxy,
      CallbackContext callbackContext, 
      Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
      ResourceModel model,
      Logger logger) {
    int index = callbackContext.getAddResourceListIndex();
    if (useResourceDiffList) {
      addAssociatedResource(callbackContext.getAddResourceList().get(index), proxy, proxyClientMap, model, logger);
    } else {
      addAssociatedResource(model.getResourceArns().get(index), proxy, proxyClientMap, model, logger);
    }
    callbackContext.setAddResourceListIndex(index + 1);
    return ProgressEvent.<ResourceModel, CallbackContext>builder()
        .resourceModel(model)
        .callbackContext(callbackContext)
        .callbackDelaySeconds(Constants.DEFAULT_CALLBACK_DELAY_SECONDS)
        .message(Constants.ADDING_RESOURCES_IN_PROGRESS)
        .status(OperationStatus.IN_PROGRESS)
        .build();
  }

  /**
   * Function to remove AssociatedResources list. It determines which resource will be added in one round based on
   * RemoveResourceListIndex in the callbackContext
   * @return send back an in progress event
   */
  protected ProgressEvent<ResourceModel, CallbackContext> removeAssociatedResources(
      AmazonWebServicesClientProxy proxy, 
      CallbackContext callbackContext,
      Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
      ResourceModel model,
      Logger logger) {
    int index = callbackContext.getRemoveResourceListIndex();
    removeAssociatedResource(callbackContext.getRemoveResourceList().get(index), proxy, proxyClientMap, model, logger);

    callbackContext.setRemoveResourceListIndex(index + 1);
    return ProgressEvent.<ResourceModel, CallbackContext>builder()
        .resourceModel(model)
        .callbackContext(callbackContext)
        .callbackDelaySeconds(Constants.DEFAULT_CALLBACK_DELAY_SECONDS)
        .message(Constants.REMOVING_RESOURCES_IN_PROGRESS)
        .status(OperationStatus.IN_PROGRESS)
        .build();
  }
}
