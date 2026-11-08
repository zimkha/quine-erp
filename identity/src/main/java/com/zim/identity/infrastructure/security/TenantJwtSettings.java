package com.zim.identity.infrastructure.security;

import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Base64;

/**
 * Signing key and lifetime of the tenant access token, read from
 * {@code quine.security.tenant.jwt.*} (the key comes from the environment
 * variable {@code QUINE_JWT_SECRET}, see {@code application.yaml}).
 *
 * <p>The secret is read as a raw string, never through
 * {@code @ConfigurationProperties}: a binding failure prints the rejected
 * value, i.e. the secret. Every failure here is a fixed message that names
 * the variable and never contains the value or a fragment of it; the Base64
 * exception (which quotes a character) is not chained.
 */
public final class TenantJwtSettings {

  public static final String SECRET_PROPERTY = "quine.security.tenant.jwt.secret";
  public static final String TTL_PROPERTY = "quine.security.tenant.jwt.ttl-minutes";

  public static final int DEFAULT_TTL_MINUTES = 30;
  public static final int MIN_TTL_MINUTES = 1;
  public static final int MAX_TTL_MINUTES = 120;
  public static final int MIN_SECRET_BYTES = 32;

  static final String SECRET_MISSING =
      "QUINE_JWT_SECRET is required: set it to a standard Base64 string "
          + "that decodes to at least 32 bytes";
  static final String SECRET_NOT_BASE64 =
      "QUINE_JWT_SECRET is not valid standard Base64";
  static final String SECRET_TOO_SHORT =
      "QUINE_JWT_SECRET is too short: it must decode to at least 32 bytes";
  static final String TTL_INVALID =
      "quine.security.tenant.jwt.ttl-minutes must be a whole number of minutes "
          + "between 1 and 120";

  private final byte[] key;
  private final Duration ttl;

  private TenantJwtSettings(byte[] key, Duration ttl) {
    this.key = key;
    this.ttl = ttl;
  }

  /** @throws IllegalStateException with a fixed message, never the value */
  public static TenantJwtSettings from(Environment environment) {
    return new TenantJwtSettings(readKey(environment), readTtl(environment));
  }

  private static byte[] readKey(Environment environment) {
    String raw;
    try {
      raw = environment.getProperty(SECRET_PROPERTY);
    } catch (RuntimeException exception) {
      // An unresolved placeholder: the variable is not set.
      throw new IllegalStateException(SECRET_MISSING);
    }
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException(SECRET_MISSING);
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(raw.strip());
    } catch (IllegalArgumentException exception) {
      // Not chained: its message quotes a character of the value.
      throw new IllegalStateException(SECRET_NOT_BASE64);
    }
    if (decoded.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(SECRET_TOO_SHORT);
    }
    return decoded;
  }

  private static Duration readTtl(Environment environment) {
    int minutes;
    try {
      String raw = environment.getProperty(TTL_PROPERTY);
      minutes = raw == null || raw.isBlank()
          ? DEFAULT_TTL_MINUTES
          : Integer.parseInt(raw.strip());
    } catch (RuntimeException exception) {
      throw new IllegalStateException(TTL_INVALID);
    }
    if (minutes < MIN_TTL_MINUTES || minutes > MAX_TTL_MINUTES) {
      throw new IllegalStateException(TTL_INVALID);
    }
    return Duration.ofMinutes(minutes);
  }

  /** A copy: the caller may not alter the key. */
  public byte[] key() {
    return key.clone();
  }

  public Duration ttl() {
    return ttl;
  }

  @Override
  public String toString() {
    return "TenantJwtSettings[key=[PROTECTED], ttl=" + ttl + "]";
  }
}
