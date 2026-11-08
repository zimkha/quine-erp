package com.zim.identity.presentation.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately thin. The password carries only {@code @NotBlank}: Spring MVC
 * logs a rejected value at DEBUG, so any other constraint on it could leak
 * it, and the registration policy (length) does not apply at login. The
 * e-mail has no {@code @Email}: a malformed address is a failed login (401),
 * not a 400.
 */
public record LoginRequest(

    @NotBlank
    @Size(max = 254)
    String email,

    @NotBlank
    String password
) {

  /** Keeps the password out of any accidental log line. */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=[PROTECTED]]";
  }
}
