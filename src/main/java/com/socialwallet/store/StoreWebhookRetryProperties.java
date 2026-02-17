package com.socialwallet.store;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.store.webhooks.medusa.retry")
public class StoreWebhookRetryProperties {
  private boolean enabled = true;

  /**
   * How often the scheduler checks for failed events to retry.
   */
  private long fixedDelayMs = 5000;

  /**
   * Max number of events retried per tick.
   */
  private int batchSize = 20;

  /**
   * Max total attempts, including the initial attempt.
   */
  private int maxAttempts = 10;

  /**
   * Base delay for exponential backoff.
   */
  private long baseDelaySeconds = 5;

  /**
   * Cap for exponential backoff.
   */
  private long maxDelaySeconds = 300;
}

