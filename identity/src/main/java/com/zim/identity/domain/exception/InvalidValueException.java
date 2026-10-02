package com.zim.identity.domain.exception;

import com.zim.shared.domain.DomainException;

/** A value object was given a value the domain does not allow. */
public final class InvalidValueException extends DomainException {

  public InvalidValueException(String code, String message) {
    super(code, message);
  }
}
