package com.socialwallet.store;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
  MedusaProperties.class,
  StoreSettlementProperties.class,
  StoreWebhookRetryProperties.class
})
public class MedusaConfig {
  @Bean
  public RestClient medusaRestClient(RestClient.Builder builder, MedusaProperties properties) {
    return builder.baseUrl(properties.getBaseUrl()).build();
  }
}
