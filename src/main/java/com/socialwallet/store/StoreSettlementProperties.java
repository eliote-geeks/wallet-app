package com.socialwallet.store;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.store.settlement")
public class StoreSettlementProperties {
  /**
   * Platform fee in basis points (bps): 100 bps = 1%.
   * Example: 500 = 5%.
   */
  private int platformFeeBps = 0;
}
