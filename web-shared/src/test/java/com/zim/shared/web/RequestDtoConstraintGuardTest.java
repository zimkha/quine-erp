package com.zim.shared.web;

import com.zim.shared.web.testing.RequestDtoConstraintGuard;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shared guard keeps the failure conditions of the former org test. */
class RequestDtoConstraintGuardTest {

  @Test
  void shouldAcceptPinnedConstraints() {
    assertThatCode(() -> RequestDtoConstraintGuard
        .assertOnlyPinnedConstraints(List.of(Pinned.class)))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailForUnpinnedConstraintAndPointToFieldErrorMessages() {
    assertThatThrownBy(() -> RequestDtoConstraintGuard
        .assertOnlyPinnedConstraints(List.of(Unpinned.class)))
        .hasMessageContaining("Min on Unpinned.count")
        .hasMessageContaining("com.zim.shared.web.FieldErrorMessages (web-shared)");
  }

  @Test
  void shouldFailForClassLevelConstraint() {
    assertThatThrownBy(() -> RequestDtoConstraintGuard
        .assertOnlyPinnedConstraints(List.of(ClassLevel.class)))
        .hasMessageContaining("class-level constraints");
  }

  @Test
  void shouldFailWhenNoDtoIsFoundInThePackage() {
    assertThatThrownBy(() -> RequestDtoConstraintGuard
        .assertOnlyPinnedConstraints("com.zim.shared.web.nothing"))
        .hasMessageContaining("no request DTO found");
  }

  record Pinned(@NotBlank String name) {
  }

  record Unpinned(@Min(1) int count) {
  }

  @AlwaysValid
  record ClassLevel(String name) {
  }

  @Target(TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = AlwaysValidator.class)
  @interface AlwaysValid {

    String message() default "invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  public static final class AlwaysValidator
      implements ConstraintValidator<AlwaysValid, Object> {

    public AlwaysValidator() {
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
      return true;
    }
  }
}
