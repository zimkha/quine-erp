package com.zim.organization.domain.valueobject;

import com.zim.organization.domain.exception.InvalidValueException;

import java.util.Objects;

public record OrganizationName(String value) {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 120;

  public OrganizationName {
    Objects.requireNonNull(value, "Organization name cannot be null");

    value = value.trim();

    if (value.length() < MIN_LENGTH) {
      throw new InvalidValueException(
          "INVALID_ORGANIZATION_NAME",
          "Organization name must contain at least " + MIN_LENGTH + " characters"
      );
    }

    if (value.length() > MAX_LENGTH) {
      throw new InvalidValueException(
          "INVALID_ORGANIZATION_NAME",
          "Organization name cannot exceed " + MAX_LENGTH + " characters"
      );
    }
  }
}
