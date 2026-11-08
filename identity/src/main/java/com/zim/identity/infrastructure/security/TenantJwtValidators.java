package com.zim.identity.infrastructure.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Everything the tenant decoder checks, in one composed validator.
 * {@code NimbusJwtDecoder#setJwtValidator} <em>replaces</em> the default
 * validators, so the defaults (expiry, not-before) are composed in here
 * explicitly: forgetting them would accept an expired token.
 *
 * <p>The {@code tenantId} claim is checked here, in the decoder, so that a
 * token without a usable tenant is refused in the filter chain (401 before
 * any 400), not later in a controller. The error descriptions never reach a
 * client: the entry point writes one fixed answer.
 */
public final class TenantJwtValidators {

  private static final OAuth2Error INVALID_TENANT =
      new OAuth2Error("invalid_token", "The tenantId claim is not usable", null);

  private TenantJwtValidators() {
  }

  /** Expiry and not-before (60 s skew), audience {@code quine-tenant}, tenant claim. */
  public static OAuth2TokenValidator<Jwt> tenant() {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefault(),
        expiryRequired(),
        audience(),
        tenantId()
    );
  }

  /**
   * The default timestamp validator accepts a token with no {@code exp}, i.e.
   * one that never expires: a tenant token must carry one.
   */
  static OAuth2TokenValidator<Jwt> expiryRequired() {
    return new JwtClaimValidator<Instant>("exp", expiry -> expiry != null);
  }

  /** The {@code aud} claim, a list, must contain the tenant audience. */
  static OAuth2TokenValidator<Jwt> audience() {
    return new JwtClaimValidator<Collection<String>>(
        "aud",
        audience -> audience != null
            && audience.contains(TenantTokenClaims.AUDIENCE)
    );
  }

  /** A string in canonical UUID form (lower case, 8-4-4-4-12). */
  static OAuth2TokenValidator<Jwt> tenantId() {
    return jwt -> {
      Object claim = jwt.getClaims().get(TenantTokenClaims.TENANT_ID);
      return claim instanceof String text && isCanonicalUuid(text)
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(INVALID_TENANT);
    };
  }

  /**
   * {@code UUID.fromString} alone also accepts {@code 1-1-1-1-1}: compare
   * with the canonical (lower-case, 8-4-4-4-12) representation.
   */
  public static boolean isCanonicalUuid(String text) {
    try {
      return UUID.fromString(text).toString().equals(text);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
