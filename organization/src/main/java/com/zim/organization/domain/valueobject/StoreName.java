package com.zim.organization.domain.valueobject;

import java.util.Objects;

public record StoreName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 120;

    public StoreName {
        Objects.requireNonNull(value, "Store name cannot be null");

        value = value.trim();

        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Store name must contain at least " + MIN_LENGTH + " characters"
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Store name cannot exceed " + MAX_LENGTH + " characters"
            );
        }
    }
}
