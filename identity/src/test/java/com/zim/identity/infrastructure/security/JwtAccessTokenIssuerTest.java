package com.zim.identity.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.zim.identity.application.result.LoginResult;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenIssuerTest {

  private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
      .plusMillis(789);

  private final SecretKey key = randomKey();
  private final JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(
      new NimbusJwtEncoder(new ImmutableSecret<>(key)), Duration.ofMinutes(30)
  );

  @Test
  void shouldIssueATokenTheTenantDecoderCanRead() {
    UserId user = UserId.generate();
    TenantId tenant = TenantId.generate();

    LoginResult result = issuer.issue(user, tenant, NOW);
    Jwt jwt = decoder(key).decode(result.accessToken());

    assertThat(result.tokenType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(1800);
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getSubject()).isEqualTo(user.value().toString());
    assertThat(jwt.getClaimAsString("tenantId"))
        .isEqualTo(tenant.value().toString());
    assertThat(jwt.getAudience()).containsExactly("quine-tenant");
    assertThat(jwt.getIssuedAt())
        .isEqualTo(NOW.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    assertThat(jwt.getExpiresAt())
        .isEqualTo(jwt.getIssuedAt().plusSeconds(result.expiresIn()));
  }

  @Test
  void shouldCarryNothingButTheExpectedClaims() {
    LoginResult result = issuer.issue(UserId.generate(), TenantId.generate(), NOW);
    Jwt jwt = decoder(key).decode(result.accessToken());

    assertThat(jwt.getClaims().keySet())
        .containsExactlyInAnyOrder("sub", "tenantId", "aud", "iat", "exp");
  }

  @Test
  void shouldNotBeReadableWithAnotherKey() {
    LoginResult result = issuer.issue(UserId.generate(), TenantId.generate(), NOW);

    assertThatThrownBy(() -> decoder(randomKey()).decode(result.accessToken()))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void shouldFollowTheConfiguredLifetime() {
    JwtAccessTokenIssuer short5 = new JwtAccessTokenIssuer(
        new NimbusJwtEncoder(new ImmutableSecret<>(key)), Duration.ofMinutes(5)
    );

    LoginResult result = short5.issue(UserId.generate(), TenantId.generate(), NOW);

    assertThat(result.expiresIn()).isEqualTo(300);
    Jwt jwt = decoder(key).decode(result.accessToken());
    assertThat(jwt.getExpiresAt())
        .isEqualTo(jwt.getIssuedAt().plusSeconds(300));
  }

  @Test
  void shouldMaskTheTokenInToString() {
    LoginResult result = issuer.issue(UserId.generate(), TenantId.generate(), NOW);

    assertThat(result.toString()).doesNotContain(result.accessToken());
  }

  private static NimbusJwtDecoder decoder(SecretKey key) {
    return NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  private static SecretKey randomKey() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
