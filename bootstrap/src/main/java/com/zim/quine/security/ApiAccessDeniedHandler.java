package com.zim.quine.security;

import com.zim.shared.web.ServletErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * 403 with the standard body and no OAuth2 detail (the default handler adds
 * {@code insufficient_scope}). No route produces it before T8 (roles): the
 * code name is T8's to rename.
 */
public final class ApiAccessDeniedHandler implements AccessDeniedHandler {

  public static final String CODE = "ACCESS_DENIED";
  public static final String MESSAGE = "Access to this resource is denied";

  private final ServletErrorWriter writer;

  public ApiAccessDeniedHandler(ServletErrorWriter writer) {
    this.writer = Objects.requireNonNull(writer, "Writer cannot be null");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException
  ) throws IOException {
    writer.write(response, HttpStatus.FORBIDDEN, CODE, MESSAGE);
  }
}
