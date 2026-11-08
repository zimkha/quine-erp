package com.zim.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Writes an {@link ApiErrorResponse} straight to a servlet response, for the
 * errors raised in a filter, where no {@code @RestControllerAdvice} runs
 * (the security entry point and access-denied handler).
 *
 * <p>It serialises with the application's own {@link JsonMapper}, the one the
 * message converters use, so the {@code timestamp} has exactly the format of
 * the advice. It never calls {@code sendError}: that would trigger the
 * container's {@code /error} dispatch and replace this body.
 */
public final class ServletErrorWriter {

  private final JsonMapper jsonMapper;

  public ServletErrorWriter(JsonMapper jsonMapper) {
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "Json mapper cannot be null");
  }

  /**
   * Writes a 401 with the {@code WWW-Authenticate} header of every 401 of
   * the application, exactly {@link GenericExceptionAdvice#WWW_AUTHENTICATE_VALUE}.
   */
  public void writeUnauthorized(
      HttpServletResponse response,
      String code,
      String message
  ) throws IOException {
    response.setHeader(
        HttpHeaders.WWW_AUTHENTICATE,
        GenericExceptionAdvice.WWW_AUTHENTICATE_VALUE
    );
    write(response, HttpStatus.UNAUTHORIZED, code, message);
  }

  public void write(
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message
  ) throws IOException {
    byte[] body = jsonMapper
        .writeValueAsString(new ApiErrorResponse(code, message, Instant.now()))
        .getBytes(StandardCharsets.UTF_8);

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setContentLength(body.length);
    response.getOutputStream().write(body);
    response.flushBuffer();
  }
}
