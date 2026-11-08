package com.zim.identity.presentation.rest.response;

/** {@code expiresIn} is in seconds. The token is masked in {@link #toString()}. */
public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {

  @Override
  public String toString() {
    return "LoginResponse[accessToken=[PROTECTED], tokenType=" + tokenType
        + ", expiresIn=" + expiresIn + "]";
  }
}
