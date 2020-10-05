package com.amazon.synthetics.canary;

import software.amazon.awssdk.services.synthetics.model.*;
import software.amazon.cloudformation.Action;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends CanaryActionHandler {
    public ListHandler() {
        super(Action.LIST);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest() {
        List<ResourceModel> models = listAllCanaries();
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(models)
            .nextToken(request.getNextToken())
            .status(OperationStatus.SUCCESS)
            .build();
    }

    private List<ResourceModel> listAllCanaries() {
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
           log(ex);
           throw new CfnInvalidRequestException(ex);
       } catch (SyntheticsException ex) {
           log(ex);
           throw new CfnGeneralServiceException(ex);
       }
       request.setNextToken(describeCanariesResponse.nextToken());
       return models;
    }
}
