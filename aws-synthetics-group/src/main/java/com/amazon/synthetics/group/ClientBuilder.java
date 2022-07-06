package com.amazon.synthetics.group;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.cloudformation.LambdaWrapper;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class ClientBuilder {
  public static SyntheticsClient getClient() {
    return SyntheticsClient.builder()
        .httpClient(LambdaWrapper.HTTP_CLIENT)
        .build();
  }

  public static SyntheticsClient getClient(Region region) {
    try {
      SyntheticsClient regionClient = SyntheticsClient.builder()
          .httpClient(LambdaWrapper.HTTP_CLIENT)
          .region(region)
          .build();
      return regionClient;
    } catch (Exception e) {
      // switching to default client
      return getClient();
    }
  }

  public static Map<Region, ProxyClient<SyntheticsClient>> getClientMap(AmazonWebServicesClientProxy proxy) {
    Map<Region, ProxyClient<SyntheticsClient>> syntheticsClientMap = new HashMap<>();
    for(Region region: Region.regions()) {
      Supplier<SyntheticsClient> clientSupplier = () -> getClient(region);
      syntheticsClientMap.put(region, proxy.newProxy(clientSupplier));
    }
    return syntheticsClientMap;
  }

  /**
   * Provide endpoint overrides for testing
   * if your SDK is not public yet.
   * Pass the region and endpoint at the time of constructing the client
   * @param region
   * @param endpointOverride
   * @return
   */
  static SyntheticsClient getClient(String region, String endpointOverride){
    return SyntheticsClient.builder()
        .overrideConfiguration(
            ClientOverrideConfiguration
                .builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(30))
                .apiCallTimeout(Duration.ofSeconds(59))
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build())
        .region(Region.of(region))
        .endpointOverride(URI.create(endpointOverride))
        .build();
  }
}
