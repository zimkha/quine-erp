package com.zim.identity.infrastructure.security;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * The real tenant of the request: the {@code tenantId} claim of the
 * authenticated token, and nothing else (never {@code sub}, another claim, a
 * header, the path or the body). The decoder already refuses a token whose
 * claim is missing or malformed, so the checks here are a safety net: they
 * fail closed with {@link TenantNotResolvedException}, never a default tenant.
 */
public final class JwtTenantProvider implements CurrentTenantProvider {

  @Override
  public TenantId currentTenant() {
    Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new TenantNotResolvedException();
    }
    Object claim = jwt.getClaims().get(TenantTokenClaims.TENANT_ID);
    if (!(claim instanceof String text) || !TenantJwtValidators.isCanonicalUuid(text)) {
      throw new TenantNotResolvedException();
    }
    return new TenantId(UUID.fromString(text));
  }
}
