package com.zim.identity.application.port;

import com.zim.identity.application.result.LoginResult;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.TenantId;

import java.time.Instant;

@FunctionalInterface
public interface AccessTokenIssuer {

  /** Issues an access token for the user of the tenant, valid from {@code issuedAt}. */
  LoginResult issue(UserId userId, TenantId tenantId, Instant issuedAt);
}
