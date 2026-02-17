package com.socialwallet.wallet;

import java.util.UUID;

public final class WalletInternalUsers {
  private WalletInternalUsers() {}

  /**
   * Internal clearing account used by capture/settlement flows.
   */
  public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  /**
   * Internal treasury account used to store platform fees.
   */
  public static final UUID PLATFORM_TREASURY_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
}

