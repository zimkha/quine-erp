package com.zim.organization.domain.valueobject;

import com.zim.organization.domain.exception.InvalidValueException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class LegalName {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 160;

  private static final Pattern MULTIPLE_SPACES =
      Pattern.compile("\\s+");

  private final String value;
  private final String normalizedValue;

  public LegalName(String value) {
    Objects.requireNonNull(value, "Legal name cannot be null");

    String formatted = MULTIPLE_SPACES
        .matcher(value.trim())
        .replaceAll(" ");

    if (formatted.length() < MIN_LENGTH
        || formatted.length() > MAX_LENGTH) {
      throw new InvalidValueException(
          "INVALID_LEGAL_NAME",
          "Legal name must contain between %d and %d characters"
              .formatted(MIN_LENGTH, MAX_LENGTH)
      );
    }

    this.value = formatted;
    this.normalizedValue =
        formatted.toUpperCase(Locale.ROOT);
  }

  public String value() {
    return value;
  }

  public String normalizedValue() {
    return normalizedValue;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }

    if (!(object instanceof LegalName other)) {
      return false;
    }

    return normalizedValue.equals(other.normalizedValue);
  }

  @Override
  public int hashCode() {
    return normalizedValue.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}