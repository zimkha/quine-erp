package com.zim.identity.infrastructure.security;

import com.zim.identity.application.port.AccessTokenIssuer;
import com.zim.identity.application.result.LoginResult;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.TenantId;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Signs a tenant access token (HS256). The token carries the user id (sub),
 * the tenant, the tenant audience and the validity; never an e-mail, a
 * password or a hash.
 */
public final class JwtAccessTokenIssuer implements AccessTokenIssuer {

  private final JwtEncoder encoder;
  private final Duration ttl;

  public JwtAccessTokenIssuer(JwtEncoder encoder, Duration ttl) {
    this.encoder = Objects.requireNonNull(encoder, "Encoder cannot be null");
    this.ttl = Objects.requireNonNull(ttl, "Ttl cannot be null");
  }

  @Override
  public LoginResult issue(UserId userId, TenantId tenantId, Instant issuedAt) {
    Objects.requireNonNull(userId, "User id cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(issuedAt, "Issue time cannot be null");

    // A JWT holds whole seconds: truncate so that exp = iat + expiresIn.
    Instant iat = issuedAt.truncatedTo(ChronoUnit.SECONDS);
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId.value().toString())
        .claim(TenantTokenClaims.TENANT_ID, tenantId.value().toString())
        .audience(List.of(TenantTokenClaims.AUDIENCE))
        .issuedAt(iat)
        .expiresAt(iat.plus(ttl))
        .build();

    // The algorithm must be explicit, or the encoder cannot choose a key.
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder
        .encode(JwtEncoderParameters.from(header, claims))
        .getTokenValue();

    return new LoginResult(
        token,
        TenantTokenClaims.TOKEN_TYPE,
        ttl.toSeconds()
    );
  }
}
