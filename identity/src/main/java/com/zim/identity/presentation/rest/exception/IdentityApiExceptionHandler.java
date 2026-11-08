package com.zim.identity.presentation.rest.exception;

import com.zim.identity.application.exception.InvalidCredentialsException;
import com.zim.identity.presentation.rest.AuthController;
import com.zim.shared.web.AdviceOrder;
import com.zim.shared.web.ApiErrorResponse;
import com.zim.shared.web.GenericExceptionAdvice;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to this module's controllers and ordered before the generic advice
 * of {@code web-shared}. It handles only the exceptions this module owns.
 */
@RestControllerAdvice(basePackageClasses = AuthController.class)
@Order(AdviceOrder.MODULE)
public class IdentityApiExceptionHandler {

  /**
   * Same status, code, message and header for every login failure. The
   * header is the one of every other 401 (RFC 9110).
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException exception
  ) {
    ResponseEntity<ApiErrorResponse> response = GenericExceptionAdvice.error(
        HttpStatus.UNAUTHORIZED,
        exception.code(),
        InvalidCredentialsException.MESSAGE
    );
    return ResponseEntity
        .status(response.getStatusCode())
        .header(
            HttpHeaders.WWW_AUTHENTICATE,
            GenericExceptionAdvice.WWW_AUTHENTICATE_VALUE
        )
        .body(response.getBody());
  }
}
