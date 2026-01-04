package com.socialwallet.admin.dto;

import lombok.Data;

@Data
public class OpenImRepairResponse {
  private int total;
  private int repaired;
  private int failed;
}
