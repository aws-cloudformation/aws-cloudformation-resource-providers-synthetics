package com.amazon.synthetics.canary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.synthetics.model.Canary;
import software.amazon.awssdk.services.synthetics.model.DescribeCanariesResponse;
import software.amazon.cloudformation.proxy.*;

import java.util.ArrayList;
import java.util.List;

import static com.amazon.synthetics.canary.Matchers.assertThatModelsAreEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends TestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ListHandler handler;

    private static ResourceModel model;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
        handler = new ListHandler();
        model = ResourceModel.builder().name("listCanary").build();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .nextToken("test")
                .build();

        final List<Canary> canaryList = new ArrayList<Canary>();
        Canary canary1 = canaryResponseObjectForTesting("1-canary-cfn-unit");
        Canary canary2 = canaryResponseObjectForTesting("2-canary-cfn-unit");

        canaryList.add(canary1);
        canaryList.add(canary2);

        final DescribeCanariesResponse describeCanariesResponse = DescribeCanariesResponse.builder()
                .canaries(canaryList)
                .build();

        doReturn(describeCanariesResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()
                );

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels().size()).isEqualTo(2);
        assertThatModelsAreEqual(response.getResourceModels().get(0), canary1);
        assertThatModelsAreEqual(response.getResourceModels().get(1), canary2);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
