package com.zim.identity.application.port;

import com.zim.identity.domain.valueobject.PasswordAttempt;
import com.zim.identity.domain.valueobject.PasswordHash;

@FunctionalInterface
public interface PasswordVerifier {

  /**
   * Whether the attempt matches the stored hash. One call is one unit of
   * hashing work, whatever the answer.
   */
  boolean matches(PasswordAttempt attempt, PasswordHash stored);
}
