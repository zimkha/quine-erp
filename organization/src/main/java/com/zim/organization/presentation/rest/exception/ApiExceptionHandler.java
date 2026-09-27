package com.zim.organization.presentation.rest.exception;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
      MethodArgumentNotValidException exception
  ) {
    String message = exception.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .sorted()
        .collect(Collectors.joining("; "));

    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
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
