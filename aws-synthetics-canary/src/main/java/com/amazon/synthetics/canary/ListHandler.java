package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends BaseHandler<CallbackContext> {
  @Override
  public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
          final AmazonWebServicesClientProxy proxy,
          final ResourceHandlerRequest<ResourceModel> request,
          final CallbackContext callbackContext,
          final Logger logger) {

      SyntheticsClient syntheticsClient = ClientBuilder.getClient();

      final List<ResourceModel> models = listAllCanaries(proxy, syntheticsClient, request, logger);

      // This Lambda will continually be re-invoked with the current state of the instance, finally succeeding when state stabilizes.
      return ProgressEvent.<ResourceModel, CallbackContext>builder()
              .resourceModels(models)
              .nextToken(request.getNextToken())
              .status(OperationStatus.SUCCESS)
              .build();
  }

    private List<ResourceModel> listAllCanaries(final AmazonWebServicesClientProxy proxy,
                                                final SyntheticsClient syntheticsClient,
                                                final ResourceHandlerRequest<ResourceModel> request,
                                                final Logger logger) {
        List<ResourceModel> models = new ArrayList<>();
        final DescribeCanariesRequest describeCanariesRequest = DescribeCanariesRequest.builder()
                .nextToken(request.getNextToken())
                .build();

        final DescribeCanariesResponse describeCanariesResponse;

       try {
           describeCanariesResponse = proxy.injectCredentialsAndInvokeV2(describeCanariesRequest, syntheticsClient::describeCanaries);

           describeCanariesResponse.canaries().forEach(canary -> {
               ResourceModel model = ResourceModel.builder().build();
               model = ModelHelper.constructModel(canary, model);
               models.add(model);
           });
       } catch (ValidationException ex) {
           logger.log(String.format("%s [%s] Validation exception while DescribeCanaries", ResourceModel.TYPE_NAME, ex.getMessage()));
           throw new CfnInvalidRequestException(ex.getMessage());
       } catch (SyntheticsException ex) {
           logger.log(String.format("%s [%s] Describe Failed", ResourceModel.TYPE_NAME, ex.getMessage()));
           throw new CfnGeneralServiceException(ex.getMessage());
       }
       request.setNextToken(describeCanariesResponse.nextToken());
       return models;
    }
}
