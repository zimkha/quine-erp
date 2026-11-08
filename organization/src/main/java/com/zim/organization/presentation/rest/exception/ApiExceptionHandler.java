package com.zim.organization.presentation.rest.exception;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.presentation.rest.OrganizationController;
import com.zim.shared.web.AdviceOrder;
import com.zim.shared.web.ApiErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.zim.shared.web.GenericExceptionAdvice.error;

/**
 * Scoped to this module's controllers and ordered before the generic
 * advice of {@code web-shared}. It handles only the exceptions this module
 * owns; every shared exception is mapped once, in the generic advice.
 */
@RestControllerAdvice(basePackageClasses = OrganizationController.class)
@Order(AdviceOrder.MODULE)
public class ApiExceptionHandler {

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
}
