package com.amazon.synthetics.group;

import com.amazon.synthetics.group.Utils.Constants;
import java.util.List;
import java.util.function.Supplier;
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
  private GroupLogger logger;

  protected AmazonWebServicesClientProxy webServiceProxy;
  protected ResourceHandlerRequest<ResourceModel> request;
  protected CallbackContext callbackContext;
  protected ResourceModel model;
  protected ProxyClient<SyntheticsClient> proxyClient;

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
    SyntheticsClient syntheticsClient = ClientBuilder.getClient();
    Supplier<SyntheticsClient> clientSupplier = () -> syntheticsClient;

    return handleRequest(proxy, request, callbackContext, proxy.newProxy(clientSupplier), logger);
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<SyntheticsClient> proxyClient,
      final Logger logger) {
    this.webServiceProxy = proxy;
    this.request = request;
    this.callbackContext = callbackContext != null ? callbackContext : CallbackContext.builder().build();
    this.model = request.getDesiredResourceState();
    this.logger = new GroupLogger(logger, action, request.getAwsAccountId(), callbackContext, model);
    this.proxyClient = proxyClient;

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

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest();

  protected void log(String message) {
    logger.log(message);
  }

  protected void log(Exception exception) {
    logger.log(exception);
  }


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
      e.printStackTrace();
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      e.printStackTrace();
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, model.getName(), e.getMessage(), e);
    } catch (final Exception e) {
      e.printStackTrace();
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  protected void addAssociatedResource(String canaryArn) {
    // Translate resource model to create group request
    // call associate resource request
    log(Constants.MAKING_ADD_ASSOCIATE);
    try {
      AssociateResourceRequest associateResourceRequest = AssociateResourceRequest.builder()
          .resourceArn(canaryArn)
          .groupIdentifier(model.getName())
          .build();
      webServiceProxy.injectCredentialsAndInvokeV2(associateResourceRequest, proxyClient.client()::associateResource);
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, canaryArn, e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

  protected void removeAssociatedResource(String canaryArn) {
    // Translate resource model to create group request
    // call associate resource request
    log(Constants.MAKING_REMOVE_ASSOCIATE);
    try {
      DisassociateResourceRequest disassociateResourceRequest = DisassociateResourceRequest.builder()
          .groupIdentifier(model.getName())
          .resourceArn(canaryArn)
          .build();
      webServiceProxy.injectCredentialsAndInvokeV2(disassociateResourceRequest, proxyClient.client()::disassociateResource);
    } catch (final ValidationException e) {
      throw new CfnInvalidRequestException(e.getMessage());
    } catch (ResourceNotFoundException e) {
      throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, canaryArn, e.getMessage(), e);
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e.getMessage());
    }
  }

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
