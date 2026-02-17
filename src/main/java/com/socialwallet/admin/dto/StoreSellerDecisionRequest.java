package com.socialwallet.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreSellerDecisionRequest {
  @Size(max = 500)
  private String rejectionReason;
}

