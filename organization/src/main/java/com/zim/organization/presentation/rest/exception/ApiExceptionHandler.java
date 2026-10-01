package com.zim.organization.presentation.rest.exception;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.presentation.rest.OrganizationController;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.DomainException;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Scoped to this module's controllers so that exception handlers of
 * different modules cannot take each other's exceptions.
 */
@RestControllerAdvice(basePackageClasses = OrganizationController.class)
public class ApiExceptionHandler {

  private static final String WWW_AUTHENTICATE_VALUE = "Bearer realm=\"quine-erp\"";

  /**
   * Each part is {@code "<field>: <text>"} with a fixed English text from
   * {@link FieldErrorMessages}, never the validator's localized message.
   * Parts are sorted in plain {@code String} order because the raw field
   * error order varies between identical requests.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
      MethodArgumentNotValidException exception
  ) {
    String message = exception.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + FieldErrorMessages.text(error))
        .distinct()
        .sorted()
        .collect(Collectors.joining("; "));

    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
  }

  /**
   * A path variable could not be converted (e.g. a malformed organization
   * id). Only the parameter name is reported; the submitted value is never
   * echoed.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(
      MethodArgumentTypeMismatchException exception
  ) {
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        exception.getName() + ": must be a valid UUID"
    );
  }

  /**
   * The body is missing, is not valid JSON or holds a value of the wrong
   * type. The parser's message is not returned, since it quotes the input.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
      HttpMessageNotReadableException exception
  ) {
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "Request body is missing or malformed"
    );
  }

  /**
   * The tenant of the request could not be established: fail closed with
   * 401. The body carries only the stable code and a generic message.
   */
  @ExceptionHandler(TenantNotResolvedException.class)
  public ResponseEntity<ApiErrorResponse> handleTenantNotResolved(
      TenantNotResolvedException exception
  ) {
    ResponseEntity<ApiErrorResponse> response = error(
        HttpStatus.UNAUTHORIZED,
        exception.code(),
        exception.getMessage()
    );
    return ResponseEntity
        .status(response.getStatusCode())
        .headers(response.getHeaders())
        .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE)
        .body(response.getBody());
  }

  @ExceptionHandler(OrganizationNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleOrganizationNotFound(
      OrganizationNotFoundException exception
  ) {
    return error(
        HttpStatus.NOT_FOUND,
        exception.code(),
        exception.getMessage()
    );
  }

  @ExceptionHandler(OrganizationAlreadyExistsException.class)
  public ResponseEntity<ApiErrorResponse> handleOrganizationAlreadyExists(
      OrganizationAlreadyExistsException exception
  ) {
    return error(
        HttpStatus.CONFLICT,
        "ORGANIZATION_ALREADY_EXISTS",
        exception.getMessage()
    );
  }

  /**
   * A business rule rejected the command given the aggregate's current
   * state (e.g. adding a store to a closed organization).
   */
  @ExceptionHandler(BusinessRuleViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessRuleViolation(
      BusinessRuleViolationException exception
  ) {
    return error(
        HttpStatus.CONFLICT,
        exception.code(),
        exception.getMessage()
    );
  }

  /**
   * Any other domain error: the request is well-formed but carries a value
   * the domain does not accept (e.g. an unsupported currency).
   */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomainException(
      DomainException exception
  ) {
    return error(
        HttpStatus.UNPROCESSABLE_CONTENT,
        exception.code(),
        exception.getMessage()
    );
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiErrorResponse> handleConcurrentModification(
      OptimisticLockingFailureException exception
  ) {
    return error(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "The organization was modified by another request; "
            + "reload it and retry"
    );
  }

  /**
   * A database constraint rejected the write. The message is kept generic
   * so that SQL details are not leaked to clients.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException exception
  ) {
    return error(
        HttpStatus.CONFLICT,
        "DATA_INTEGRITY_VIOLATION",
        "The request conflicts with existing data"
    );
  }

  private static ResponseEntity<ApiErrorResponse> error(
      HttpStatus status,
      String code,
      String message
  ) {
    return ResponseEntity
        .status(status)
        .body(new ApiErrorResponse(code, message, Instant.now()));
  }
}
