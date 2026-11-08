package com.zim.identity.application.result;

/** {@code expiresIn} is in seconds. The token is masked in {@link #toString()}. */
public record LoginResult(
    String accessToken,
    String tokenType,
    long expiresIn
) {

  @Override
  public String toString() {
    return "LoginResult[accessToken=[PROTECTED], tokenType=" + tokenType
        + ", expiresIn=" + expiresIn + "]";
  }
}
