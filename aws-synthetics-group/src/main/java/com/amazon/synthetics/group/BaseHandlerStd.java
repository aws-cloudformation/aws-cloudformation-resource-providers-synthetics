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
  //Adding default region for testing purposes
  protected Region region = Region.US_WEST_2;
  private final Action action;
  private GroupLogger logger;

  protected AmazonWebServicesClientProxy webServiceProxy;
  protected ResourceHandlerRequest<ResourceModel> request;
  protected CallbackContext callbackContext;
  protected ResourceModel model;
  protected ProxyClient<SyntheticsClient> proxyClient;
  // This is to handle making multiregion calls for associate resource and dissociate resource
  protected Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap;

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
    region = request.getRegion()!= null ? Region.of(request.getRegion()) : Region.US_WEST_2;
    proxyClientMap = ClientBuilder.getClientMap(proxy);
    return handleRequest(proxy, request, callbackContext, proxyClientMap, logger);
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
  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap,
      final Logger logger) {
    this.webServiceProxy = proxy;
    this.request = request;
    this.callbackContext = callbackContext != null ? callbackContext : CallbackContext.builder().build();
    this.model = request.getDesiredResourceState();
    this.logger = new GroupLogger(logger, action, request.getAwsAccountId(), callbackContext, model);
    this.proxyClientMap = proxyClientMap;
    this.proxyClient = proxyClientMap.get(region);

    log("map: " + this.proxyClientMap);
    log("map: " + this.proxyClientMap.get(region));
    log(Constants.INVOKING_HANDLER_MSG);
    ProgressEvent<ResourceModel, CallbackContext> response;
    try {
      response = handleRequest();
    } catch (Exception e) {
      log(e);
      throw e;
    }
    log(Constants.INVOKING_HANDLER_FINISHED_MSG);
    return response;

  }

  /**
   * Overridden in every handler based on the action
   * @return
   */
  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest();

  protected void log(String message) {
    logger.log(message);
  }

  protected void log(Exception exception) {
    logger.log(exception);
  }

  /**
   * Wrapper to call getGroup with Synthetics client and handle
   * the response/ error
   * @return Group
   */
  protected Group getGroupOrThrow() {
    try {
      log(Constants.GET_GROUP_CALL);
      GetGroupRequest getGroupRequest = com.amazon.synthetics.group.Translator.translateToReadRequest(model);
      GetGroupResponse getGroupResponse = webServiceProxy.injectCredentialsAndInvokeV2(getGroupRequest,
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
  protected List<String> getGroupResourcesOrThrow() {
    try {
      log(Constants.LIST_GROUP_RESOURCES_CALL);
      ListGroupResourcesRequest listGroupResourcesRequest = ListGroupResourcesRequest.builder()
          .groupIdentifier(model.getName())
          .build();
      ListGroupResourcesResponse listGroupResourcesResponse = webServiceProxy.injectCredentialsAndInvokeV2(listGroupResourcesRequest,
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
  protected void addAssociatedResource(String canaryArn) {
    // Translate resource model to create group request
    // call associate resource request
    log(Constants.MAKING_ADD_ASSOCIATE);
    try {
      Arn resourceArn = Arn.fromString(canaryArn);
      AssociateResourceRequest associateResourceRequest = AssociateResourceRequest.builder()
          .resourceArn(canaryArn)
          .groupIdentifier(model.getName())
          .build();
      webServiceProxy.injectCredentialsAndInvokeV2(associateResourceRequest,
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
  protected void removeAssociatedResource(String canaryArn) {
    log(Constants.MAKING_REMOVE_ASSOCIATE);
    try {
      Arn resourceArn = Arn.fromString(canaryArn);
      DisassociateResourceRequest disassociateResourceRequest = DisassociateResourceRequest.builder()
          .groupIdentifier(model.getName())
          .resourceArn(canaryArn)
          .build();
      webServiceProxy.injectCredentialsAndInvokeV2(disassociateResourceRequest, proxyClientMap.get(Region.of(resourceArn.getRegion())).client()::disassociateResource);
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
  protected ProgressEvent<ResourceModel, CallbackContext> addAssociatedResources(boolean useResourceDiffList) {
    int index = callbackContext.getAddResourceListIndex();
    if (useResourceDiffList) {
      addAssociatedResource(callbackContext.getAddResourceList().get(index));
    } else {
      addAssociatedResource(model.getResourceArns().get(index));
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
  protected ProgressEvent<ResourceModel, CallbackContext> removeAssociatedResources() {
    int index = callbackContext.getRemoveResourceListIndex();
    removeAssociatedResource(callbackContext.getRemoveResourceList().get(index));

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
