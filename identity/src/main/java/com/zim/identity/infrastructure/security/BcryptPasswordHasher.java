package com.zim.identity.infrastructure.security;

import com.zim.identity.application.port.PasswordHasher;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Objects;

/** BCrypt with a random salt per hash: the same password hashes differently. */
public final class BcryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public PasswordHash hash(PlainPassword password) {
    Objects.requireNonNull(password, "Password cannot be null");
    return new PasswordHash(encoder.encode(password.reveal()));
  }
}
