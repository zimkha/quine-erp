package com.zim.quine;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The tenant token key of the bootstrap ITs: random, generated once per JVM
 * (contexts are cached across classes, so every class must see the same
 * value), never written anywhere. Standard Base64 of 32 random bytes.
 */
public final class TestJwtSecret {

  private static final byte[] KEY = new byte[32];

  static {
    new SecureRandom().nextBytes(KEY);
  }

  private TestJwtSecret() {
  }

  public static String value() {
    return Base64.getEncoder().encodeToString(KEY);
  }

  public static byte[] bytes() {
    return KEY.clone();
  }
}
