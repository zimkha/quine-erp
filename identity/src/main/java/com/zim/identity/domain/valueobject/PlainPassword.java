package com.zim.identity.domain.valueobject;

import com.zim.identity.domain.exception.InvalidValueException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A password as typed by the user. It exists only long enough to be hashed,
 * and never prints its value: {@link #toString()} is masked so an accidental
 * log line or error message cannot leak it.
 *
 * <p>Bounds: at least 12 characters, and at most 72 bytes in UTF-8, because
 * BCrypt silently ignores everything after byte 72.
 */
public final class PlainPassword {

  static final int MIN_CHARACTERS = 12;
  static final int MAX_BYTES = 72;

  private final String value;

  public PlainPassword(String value) {
    Objects.requireNonNull(value, "Password cannot be null");

    if (value.isBlank()
        || value.length() < MIN_CHARACTERS
        || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new InvalidValueException(
          "INVALID_PASSWORD",
          "Password must contain at least %d characters and at most %d bytes"
              .formatted(MIN_CHARACTERS, MAX_BYTES)
      );
    }

    this.value = value;
  }

  /** For the hasher only. Never log or return this. */
  public String reveal() {
    return value;
  }

  @Override
  public String toString() {
    return "[PROTECTED]";
  }
}
