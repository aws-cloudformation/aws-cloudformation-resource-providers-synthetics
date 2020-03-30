package com.amazon.synthetics.canary;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.synthetics.SyntheticsClient;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {
    /**
     * The CFN handler timeout is 60s:
     *  https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-walkthrough.html
     *    - https://sage.amazon.com/questions/767822
     *    Synthetics regularly has long latencies (20s or higher) after creating and before starting as Lambdas are being
     *    provisioned.
     *    We'll use 3 30s timeouts, which should handle any initial-use latency and be within the CFN limi
     * @return
     */

    static SyntheticsClient getClient() {
        return SyntheticsClient
                .builder()
                .overrideConfiguration(
                        ClientOverrideConfiguration
                                .builder()
                                .apiCallAttemptTimeout(Duration.ofSeconds(30))
                                .apiCallTimeout(Duration.ofSeconds(59))
                                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                                .build())
                // It is safe to close this client, which will not close the static http client
                //   - https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/client-configuration-http.html
                .httpClient(LambdaWrapper.HTTP_CLIENT)
                .build();
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
        return SyntheticsClient
                .builder()
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
