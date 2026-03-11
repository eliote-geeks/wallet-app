package com.socialwallet.store.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class StoreSellerOrderDetailDto extends StoreSellerOrderSummaryDto {
  private JsonNode items;
}
