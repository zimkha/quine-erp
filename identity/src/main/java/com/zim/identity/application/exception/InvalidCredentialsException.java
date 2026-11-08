package com.zim.identity.application.exception;

/**
 * The login failed: unknown or malformed e-mail, wrong password, or a
 * password outside the accepted bounds. One exception, one fixed message, no
 * e-mail and no cause, so nothing reveals which of these it was.
 *
 * <p>Deliberately not a {@code DomainException}: the generic 422 advice must
 * never be able to claim it.
 */
public final class InvalidCredentialsException extends RuntimeException {

  public static final String CODE = "INVALID_CREDENTIALS";

  public static final String MESSAGE = "The e-mail or the password is incorrect";

  public InvalidCredentialsException() {
    super(MESSAGE, null, false, false);
  }

  public String code() {
    return CODE;
  }
}
