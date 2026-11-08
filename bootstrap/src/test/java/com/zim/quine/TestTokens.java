package com.zim.quine;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens for the chain tests, signed with the per-run test key
 * ({@link TestJwtSecret}) unless another key is given. Nothing here is a real
 * credential. {@link #claims} gives a valid claim set to alter.
 */
public final class TestTokens {

  private TestTokens() {
  }

  /** A valid tenant token for the tenant. */
  public static String valid(UUID tenant) {
    return sign(TestJwtSecret.bytes(), claims(tenant));
  }

  /** The claims of a valid tenant token: sub, tenantId, aud, iat, exp. */
  public static Map<String, Object> claims(UUID tenant) {
    Instant now = Instant.now();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("tenantId", tenant.toString());
    claims.put("aud", List.of("quine-tenant"));
    claims.put("iat", now);
    claims.put("exp", now.plus(Duration.ofMinutes(10)));
    return claims;
  }

  public static String sign(byte[] key, Map<String, Object> claims) {
    return sign(key, MacAlgorithm.HS256, claims);
  }

  public static String sign(byte[] key, MacAlgorithm algorithm, Map<String, Object> claims) {
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(
        new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(algorithm).build(),
        JwtClaimsSet.builder().claims(existing -> existing.putAll(claims)).build()
    )).getTokenValue();
  }

  /** A random key of the given size, for "signed with another key". */
  public static byte[] randomKey(int bytes) {
    byte[] key = new byte[bytes];
    new SecureRandom().nextBytes(key);
    return key;
  }

  /** An unsigned token ({@code alg: none}) with the given claims as JSON. */
  public static String unsigned(UUID tenant) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
    long exp = Instant.now().plus(Duration.ofMinutes(10)).getEpochSecond();
    String payload = encoder.encodeToString((
        "{\"sub\":\"x\",\"tenantId\":\"" + tenant + "\",\"aud\":[\"quine-tenant\"],"
            + "\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".";
  }
}
