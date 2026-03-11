package com.socialwallet.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreSellerApplicationRequest {
  @NotBlank
  @Size(max = 120)
  private String shopName;

  @Size(max = 500)
  private String description;
}

