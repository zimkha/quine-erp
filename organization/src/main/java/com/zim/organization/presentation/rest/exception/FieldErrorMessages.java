package com.zim.organization.presentation.rest.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.springframework.validation.FieldError;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Fixed English text for a request field error, built from the constraint
 * type. The validator's interpolated message is never used: its language
 * depends on the request and JVM locales, and it can quote the regex.
 * Numbers are rendered without the default locale.
 */
public final class FieldErrorMessages {

  private static final String NOT_BLANK = "must not be blank";
  private static final String NOT_NULL = "must not be null";
  private static final String PATTERN = "must match the required format";
  private static final String FALLBACK = "is invalid";

  private FieldErrorMessages() {
  }

  /**
   * The text that follows {@code "<field>: "}, e.g. {@code must not be
   * blank}. A constraint without a pinned text, or a field error that does
   * not come from a constraint (e.g. a binding type mismatch), gives
   * {@code is invalid}.
   */
  public static String text(FieldError fieldError) {
    if (!fieldError.contains(ConstraintViolation.class)) {
      return FALLBACK;
    }
    ConstraintDescriptor<?> descriptor = fieldError
        .unwrap(ConstraintViolation.class)
        .getConstraintDescriptor();
    Class<? extends Annotation> type =
        descriptor.getAnnotation().annotationType();

    if (type == NotBlank.class) {
      return NOT_BLANK;
    }
    if (type == NotNull.class) {
      return NOT_NULL;
    }
    if (type == Pattern.class) {
      return PATTERN;
    }
    if (type == Size.class) {
      Map<String, Object> attributes = descriptor.getAttributes();
      return "size must be between "
          + Integer.toString((Integer) attributes.get("min"))
          + " and "
          + Integer.toString((Integer) attributes.get("max"));
    }
    return FALLBACK;
  }
}
