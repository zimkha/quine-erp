package com.zim.identity.domain.valueobject;

import com.zim.identity.domain.exception.InvalidValueException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An e-mail address, trimmed and lower-cased so that uniqueness is
 * case-insensitive. The check is deliberately structural: whether the
 * address really exists is not the domain's concern.
 *
 * <p>Only ASCII is accepted. Java and Postgres disagree on how to trim and
 * lower-case some non-ASCII characters, so a non-ASCII address could pass
 * here and then break the database's normalization CHECK. Internationalized
 * addresses are a possible later extension.
 */
public final class Email {

  private static final int MAX_LENGTH = 254;

  private static final Pattern STRUCTURE = Pattern.compile(
      "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+$"
  );

  private final String value;

  public Email(String value) {
    Objects.requireNonNull(value, "E-mail cannot be null");

    String normalized = value.trim().toLowerCase(Locale.ROOT);

    if (normalized.length() > MAX_LENGTH
        || !STRUCTURE.matcher(normalized).matches()) {
      throw new InvalidValueException(
          "INVALID_EMAIL",
          "E-mail must be a valid address of at most %d characters"
              .formatted(MAX_LENGTH)
      );
    }

    this.value = normalized;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object object) {
    return this == object
        || (object instanceof Email other && value.equals(other.value));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
