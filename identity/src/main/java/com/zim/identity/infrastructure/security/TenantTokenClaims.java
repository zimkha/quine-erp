package com.zim.identity.infrastructure.security;

/** Names and values of the tenant access token. */
public final class TenantTokenClaims {

  /** The tenant of the user, as a canonical UUID. */
  public static final String TENANT_ID = "tenantId";

  /** Audience of every tenant token; the tenant decoder (T7b-2) requires it. */
  public static final String AUDIENCE = "quine-tenant";

  public static final String TOKEN_TYPE = "Bearer";

  private TenantTokenClaims() {
  }
}
