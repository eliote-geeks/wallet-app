package com.socialwallet.config;

public final class RbacExpressions {
  public static final String PLATFORM_USER = "hasAnyRole('USER','SELLER','MODERATOR','ADMIN')";
  public static final String SELLER_OR_ADMIN = "hasAnyRole('SELLER','ADMIN')";
  public static final String MODERATOR_OR_ADMIN = "hasAnyRole('MODERATOR','ADMIN')";
  public static final String ADMIN = "hasRole('ADMIN')";

  private RbacExpressions() {}
}
