package com.zim.identity.domain.valueobject;

import java.util.Objects;

/**
 * A password typed at login. Unlike {@link PlainPassword} it carries no
 * policy: whatever was typed is checked against the stored hash, so a short,
 * long or odd password is a failed login (401), never a validation error.
 * It never prints its value. No {@code equals}: two attempts are never
 * compared.
 */
public final class PasswordAttempt {

  private final String value;

  public PasswordAttempt(String value) {
    this.value = Objects.requireNonNull(value, "Password cannot be null");
  }

  /** For the verifier only. Never log or return this. */
  public String reveal() {
    return value;
  }

  @Override
  public String toString() {
    return "[PROTECTED]";
  }
}
