package com.zim.identity.infrastructure.security;

import com.zim.identity.application.port.PasswordHasher;
import com.zim.identity.application.port.PasswordVerifier;
import com.zim.identity.domain.valueobject.PasswordAttempt;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * BCrypt with a random salt per hash: the same password hashes differently.
 * Hashing and verification share one encoder, so a hash made here (e.g. the
 * dummy hash of the login) has the same cost as the real ones.
 */
public final class BcryptPasswordHasher
    implements PasswordHasher, PasswordVerifier {

  /** BCrypt ignores every byte after the 72nd. */
  static final int MAX_BYTES = 72;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public PasswordHash hash(PlainPassword password) {
    Objects.requireNonNull(password, "Password cannot be null");
    return new PasswordHash(encoder.encode(password.reveal()));
  }

  /**
   * {@code BCryptPasswordEncoder.matches} truncates a long password instead
   * of rejecting it, so an attempt whose first 72 bytes match would pass.
   * The check is made anyway (one unit of work on every path) and then
   * refused when the attempt is over 72 bytes.
   */
  @Override
  public boolean matches(PasswordAttempt attempt, PasswordHash stored) {
    Objects.requireNonNull(attempt, "Password attempt cannot be null");
    Objects.requireNonNull(stored, "Password hash cannot be null");

    String value = attempt.reveal();
    boolean matches = encoder.matches(value, stored.value());
    boolean withinLimit =
        value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    return matches && withinLimit;
  }
}
