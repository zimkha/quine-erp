package com.zim.identity.domain.valueobject;

import java.util.Objects;

/** The stored form of a password. Never printed, never returned. */
public record PasswordHash(String value) {

  public PasswordHash {
    Objects.requireNonNull(value, "Password hash cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Password hash cannot be blank");
    }
  }

  @Override
  public String toString() {
    return "[PROTECTED]";
  }
}
