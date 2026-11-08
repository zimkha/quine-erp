package com.zim.quine.security;

import com.zim.shared.web.ServletErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Objects;

/**
 * Answers every authentication failure of a chain with one 401: the header
 * {@code WWW-Authenticate: Bearer realm="quine-erp"} and the standard
 * {@code ApiErrorResponse} body. It is parameterised by code and message, one
 * instance per chain (the tenant chain uses {@code TENANT_NOT_RESOLVED}; the
 * platform chain will use its own code). It never reveals why the request was
 * refused and never delegates to {@code BearerTokenAuthenticationEntryPoint},
 * which would add {@code error=} and {@code error_description=} to the header.
 */
public final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ServletErrorWriter writer;
  private final String code;
  private final String message;

  public ApiAuthenticationEntryPoint(
      ServletErrorWriter writer,
      String code,
      String message
  ) {
    this.writer = Objects.requireNonNull(writer, "Writer cannot be null");
    this.code = Objects.requireNonNull(code, "Code cannot be null");
    this.message = Objects.requireNonNull(message, "Message cannot be null");
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException
  ) throws IOException {
    writer.writeUnauthorized(response, code, message);
  }
}
