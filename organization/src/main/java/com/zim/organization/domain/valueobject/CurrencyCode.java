package com.zim.organization.domain.valueobject;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record CurrencyCode(String value) {

    public CurrencyCode {
        Objects.requireNonNull(value, "Currency code cannot be null");

        value = value.trim().toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported ISO 4217 currency code: " + value,
                    exception
            );
        }
    }

    public static CurrencyCode xof() {
        return new CurrencyCode("XOF");
    }
}
