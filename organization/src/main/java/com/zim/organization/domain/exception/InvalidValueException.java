package com.zim.organization.domain.exception;

import com.zim.shared.domain.DomainException;

/**
 * Thrown when a value object is given a value that is not allowed in the
 * domain, e.g. a malformed store code or an unsupported currency.
 */
public final class InvalidValueException extends DomainException {

  public InvalidValueException(String code, String message) {
    super(code, message);
  }
}
