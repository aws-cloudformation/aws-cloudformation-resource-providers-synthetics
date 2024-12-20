package com.amazon.synthetics.group;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;
import software.amazon.awssdk.services.synthetics.model.CreateGroupRequest;
import software.amazon.awssdk.services.synthetics.model.CreateGroupResponse;
import software.amazon.awssdk.services.synthetics.model.GetGroupRequest;
import software.amazon.awssdk.services.synthetics.model.GetGroupResponse;
import software.amazon.awssdk.services.synthetics.model.Group;
import software.amazon.awssdk.services.synthetics.model.ListGroupResourcesRequest;
import software.amazon.awssdk.services.synthetics.model.ListGroupResourcesResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;
  @Mock
  protected AmazonWebServicesClientProxy proxy;

  @Mock
  protected ProxyClient<SyntheticsClient> proxyClient;

  @Mock
  protected SyntheticsClient syntheticsClient;

  @Mock
  protected Map<Region, ProxyClient<SyntheticsClient>> proxyClientMap;

  @BeforeEach
  public void setup() {
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    syntheticsClient = mock(SyntheticsClient.class);
    proxyClientMap = generateMockClientMap();
    proxyClient = MOCK_PROXY(proxy, syntheticsClient);
  }

  private Map<Region, ProxyClient<SyntheticsClient>> generateMockClientMap() {
    Map<Region, ProxyClient<SyntheticsClient>> map = new HashMap<>();
    for(Region region:  Region.regions()) {
      map.put(region, MOCK_PROXY(proxy, syntheticsClient));
    }
    return map;
  }

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();
  }
  static ProxyClient<SyntheticsClient> MOCK_PROXY(
    final AmazonWebServicesClientProxy proxy,
    final SyntheticsClient syntheticsClient) {
    return new ProxyClient<SyntheticsClient>() {
      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
      injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
        return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      CompletableFuture<ResponseT>
      injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
      IterableT
      injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
        return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
      injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public SyntheticsClient client() {
        return syntheticsClient;
      }
    };
  }
  protected String GROUP_NAME_FOR_TEST = "test-group";

  protected CallbackContext createFirstCallBackContext() {
    return CallbackContext.builder().build();
  }

  protected List<String> generateListOfCanaryArns() {
   return generateListOfCanaryArns("us-west-2");
  }

  protected List<String> generateListOfCanaryArns(String region) {
    List<String> canaryArns = new ArrayList<>();
    for(int index = 0 ; index < 20; index ++) {
      canaryArns.add("arn:aws:synthetics:" + region + ":761914923529:canary:canary-" + index);

    }
    return canaryArns;
  }

  protected void configureGetGroupResourcesResponse(List<String> canaryArns) {
    doReturn(ListGroupResourcesResponse.builder().resources(canaryArns).build())
        .when(proxyClient.client()).listGroupResources(any(ListGroupResourcesRequest.class));
  }

  protected void configureGetGroupResponse(String groupName) {
    Group group = Group.builder().name(GROUP_NAME_FOR_TEST).id("groupId").build();
    when(syntheticsClient.getGroup(any(GetGroupRequest.class)))
        .thenReturn(GetGroupResponse.builder().group(group).build());
  }
}
