package com.zim.organization.domain.valueobject;

import com.zim.organization.domain.exception.InvalidValueException;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CurrencyCode(String value) {

  /**
   * Currencies the platform supports. This is the source of truth; the
   * {@code ck_organizations_currency} check constraint only mirrors it as a
   * safety net and must be migrated whenever this list changes.
   */
  private static final Set<String> SUPPORTED = Set.of("XOF", "EUR", "USD");

  public CurrencyCode {
    Objects.requireNonNull(value, "Currency code cannot be null");

    value = value.trim().toUpperCase(Locale.ROOT);

    if (!SUPPORTED.contains(value)) {
      throw new InvalidValueException(
          "UNSUPPORTED_CURRENCY",
          "Unsupported currency code '%s'; supported currencies are EUR, USD, XOF"
              .formatted(value)
      );
    }
  }

  public static CurrencyCode xof() {
    return new CurrencyCode("XOF");
  }
}
