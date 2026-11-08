package com.zim.identity.infrastructure.configuration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tenant decoder, one validator at a time: signature and algorithm,
 * expiry, not-before, audience and the canonical {@code tenantId}. The key is
 * random per run, nothing is a real credential.
 */
class TenantJwtDecoderTest {

  private static final UUID TENANT = UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");

  private final byte[] key = randomBytes(32);

  @Test
  void shouldAcceptAValidTokenAndReadItsClaims() {
    withDecoder(decoder -> {
      Jwt jwt = decoder.decode(sign(key, claims(c -> { })));

      assertThat(jwt.getClaimAsString("tenantId")).isEqualTo(TENANT.toString());
      assertThat(jwt.getAudience()).containsExactly("quine-tenant");
    });
  }

  @Test
  void shouldAcceptAnAudienceListThatContainsTheTenantAudience() {
    withDecoder(decoder -> assertThat(decoder.decode(sign(key, claims(
        c -> c.put("aud", List.of("quine-tenant", "other")))))).isNotNull());
  }

  @Test
  void shouldRefuseAnotherKey() {
    withDecoder(decoder ->
        assertRefused(decoder, sign(randomBytes(32), claims(c -> { }))));
  }

  @Test
  void shouldRefuseAnotherAlgorithm() {
    withDecoder(decoder ->
        assertRefused(decoder, sign(randomBytes(64), MacAlgorithm.HS384, claims(c -> { }))));
  }

  @Test
  void shouldRefuseAnUnsignedToken() {
    withDecoder(decoder -> {
      Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      String token = encoder.encodeToString("{\"alg\":\"none\"}".getBytes())
          + "." + encoder.encodeToString(("{\"tenantId\":\"" + TENANT
          + "\",\"aud\":[\"quine-tenant\"]}").getBytes()) + ".";

      assertRefused(decoder, token);
    });
  }

  @Test
  void shouldRefuseAnExpiredTokenPastTheClockSkew() {
    withDecoder(decoder -> assertRefused(decoder, sign(key, claims(c -> {
      c.put("iat", Instant.now().minus(Duration.ofMinutes(30)));
      c.put("exp", Instant.now().minus(Duration.ofMinutes(5)));
    }))));
  }

  @Test
  void shouldRefuseATokenWithoutExpiry() {
    withDecoder(decoder -> assertRefused(decoder, sign(key, claims(c -> c.remove("exp")))));
  }

  @Test
  void shouldRefuseATokenNotYetValid() {
    withDecoder(decoder -> assertRefused(decoder, sign(key, claims(
        c -> c.put("nbf", Instant.now().plus(Duration.ofMinutes(10)))))));
  }

  @Test
  void shouldRefuseAnotherAudienceAndAMissingOne() {
    withDecoder(decoder -> {
      assertRefused(decoder, sign(key, claims(c -> c.put("aud", List.of("quine-platform")))));
      assertRefused(decoder, sign(key, claims(c -> c.put("aud", List.of()))));
      assertRefused(decoder, sign(key, claims(c -> c.remove("aud"))));
    });
  }

  @Test
  void shouldRefuseAMissingOrNonCanonicalTenantId() {
    withDecoder(decoder -> {
      assertRefused(decoder, sign(key, claims(c -> c.remove("tenantId"))));
      for (Object claim : List.of(
          "not-a-uuid", "", "1-1-1-1-1", TENANT.toString().toUpperCase(),
          TENANT.toString().replace("-", ""), " " + TENANT, TENANT + " ",
          12345, List.of(TENANT.toString()))) {
        assertRefused(decoder, sign(key, claims(c -> c.put("tenantId", claim))));
      }
    });
  }

  @Test
  void shouldExposeTheDecoderAsANamedBean() {
    // The decoder is a named bean: the chain qualifies it, nothing relies on
    // a single unqualified JwtDecoder.
    new ApplicationContextRunner()
        .withUserConfiguration(TenantJwtConfiguration.class)
        .withPropertyValues(secretProperty())
        .run(context -> assertThat(context).hasBean("tenantJwtDecoder"));
  }

  private void assertRefused(JwtDecoder decoder, String token) {
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  private void withDecoder(Consumer<JwtDecoder> test) {
    new ApplicationContextRunner()
        .withUserConfiguration(TenantJwtConfiguration.class)
        .withPropertyValues(secretProperty())
        .run(context -> test.accept(context.getBean("tenantJwtDecoder", JwtDecoder.class)));
  }

  private String secretProperty() {
    return "quine.security.tenant.jwt.secret=" + Base64.getEncoder().encodeToString(key);
  }

  private static Map<String, Object> claims(Consumer<Map<String, Object>> change) {
    Instant now = Instant.now();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("tenantId", TENANT.toString());
    claims.put("aud", List.of("quine-tenant"));
    claims.put("iat", now);
    claims.put("exp", now.plus(Duration.ofMinutes(10)));
    change.accept(claims);
    return claims;
  }

  private static String sign(byte[] key, Map<String, Object> claims) {
    return sign(key, MacAlgorithm.HS256, claims);
  }

  private static String sign(byte[] key, MacAlgorithm algorithm, Map<String, Object> claims) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")))
        .encode(JwtEncoderParameters.from(
            JwsHeader.with(algorithm).build(),
            JwtClaimsSet.builder().claims(existing -> existing.putAll(claims)).build()))
        .getTokenValue();
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }
}
