package com.zim.identity.application.command;

import java.util.Objects;

/** Both fields are raw input, so the password is masked in {@link #toString()}. */
public record LoginCommand(
    String email,
    String password
) {

  public LoginCommand {
    Objects.requireNonNull(email, "E-mail cannot be null");
    Objects.requireNonNull(password, "Password cannot be null");
  }

  @Override
  public String toString() {
    return "LoginCommand[email=" + email + ", password=[PROTECTED]]";
  }
}
