package com.zim.organization.domain.valueobject;

import com.zim.organization.domain.exception.InvalidValueException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record StoreCode(String value) {

  private static final Pattern FORMAT =
      Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,19}$");

  public StoreCode {
    Objects.requireNonNull(value, "Store code cannot be null");

    value = value.trim().toUpperCase(Locale.ROOT);

    if (!FORMAT.matcher(value).matches()) {
      throw new InvalidValueException(
          "INVALID_STORE_CODE",
          "Store code must contain between 2 and 20 letters, digits, '-' or '_'"
      );
    }
  }
}