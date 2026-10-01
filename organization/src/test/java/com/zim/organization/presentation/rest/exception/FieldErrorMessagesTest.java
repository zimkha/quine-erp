package com.zim.organization.presentation.rest.exception;

import com.zim.organization.testing.DefaultLocaleExtension;
import com.zim.organization.testing.LocaleScenario;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Locale;

import static java.lang.annotation.ElementType.FIELD;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Field errors are produced the way Spring MVC produces them, by
 * {@link SpringValidatorAdapter} over a real validator, so they wrap the
 * {@code ConstraintViolation} exactly as in a 400 response.
 */
@ExtendWith(DefaultLocaleExtension.class)
class FieldErrorMessagesTest {

  private static ValidatorFactory validatorFactory;

  @BeforeAll
  static void createValidatorFactory() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void closeValidatorFactory() {
    validatorFactory.close();
  }

  @Test
  void shouldGivePinnedTextForNotBlank() {
    assertThat(textOf("blank")).isEqualTo("must not be blank");
  }

  @Test
  void shouldGivePinnedTextForNotNull() {
    assertThat(textOf("missing")).isEqualTo("must not be null");
  }

  @Test
  void shouldGivePinnedTextWithoutRegexForPattern() {
    assertThat(textOf("code")).isEqualTo("must match the required format");
  }

  /**
   * min and max are read by name: the constraint's message arguments are
   * alphabetical ({@code max} before {@code min}).
   */
  @Test
  void shouldGiveSizeBoundsInMinMaxOrder() {
    assertThat(textOf("name")).isEqualTo("size must be between 2 and 160");
  }

  @Test
  void shouldKeepBetweenWordingWhenMinEqualsMax() {
    assertThat(textOf("currency")).isEqualTo("size must be between 3 and 3");
  }

  @Test
  void shouldRenderSizeBoundsWithAsciiDigitsWhateverTheDefaultLocale() {
    Locale.setDefault(LocaleScenario.ARABIC_EGYPT);

    assertThat(textOf("name")).isEqualTo("size must be between 2 and 160");
  }

  @Test
  void shouldIgnoreDefaultLocaleForEveryPinnedText() {
    Locale.setDefault(Locale.FRENCH);

    assertThat(textOf("blank")).isEqualTo("must not be blank");
    assertThat(textOf("missing")).isEqualTo("must not be null");
    assertThat(textOf("code")).isEqualTo("must match the required format");
  }

  @Test
  void shouldFallBackForConstraintWithoutPinnedText() {
    assertThat(textOf("email")).isEqualTo("is invalid");
  }

  /**
   * The constraint is identified by its annotation class, not by the error
   * code: a custom constraint that is also named {@code Size} has no pinned
   * text.
   */
  @Test
  void shouldFallBackForCustomConstraintSharingPinnedSimpleName() {
    FieldError error = fieldError("custom");

    assertThat(error.getCode()).isEqualTo("Size");
    assertThat(FieldErrorMessages.text(error)).isEqualTo("is invalid");
  }

  @Test
  void shouldFallBackForFieldErrorWithoutConstraintViolation() {
    FieldError typeMismatch = new FieldError("obj", "field", "typeMismatch");

    assertThat(FieldErrorMessages.text(typeMismatch)).isEqualTo("is invalid");
  }

  private static String textOf(String field) {
    return FieldErrorMessages.text(fieldError(field));
  }

  private static FieldError fieldError(String field) {
    Sample invalid =
        new Sample(" ", null, "X", "XO", "a b", "not-an-email", "x");
    BindingResult errors = new DirectFieldBindingResult(invalid, "sample");
    new SpringValidatorAdapter(validatorFactory.getValidator())
        .validate(invalid, errors);

    List<FieldError> fieldErrors = errors.getFieldErrors(field);
    assertThat(fieldErrors).as("errors on %s", field).hasSize(1);
    return fieldErrors.getFirst();
  }

  record Sample(
      @NotBlank String blank,
      @NotNull String missing,
      @jakarta.validation.constraints.Size(min = 2, max = 160) String name,
      @jakarta.validation.constraints.Size(min = 3, max = 3) String currency,
      @Pattern(regexp = "^[a-z]+$") String code,
      @Email String email,
      @Size String custom
  ) {
  }

  /** A custom constraint whose simple name clashes with the jakarta one. */
  @Target(FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = AlwaysInvalid.class)
  @interface Size {

    String message() default "custom size";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  /** Public with a public constructor, so the validator can create it. */
  public static final class AlwaysInvalid
      implements ConstraintValidator<Size, String> {

    public AlwaysInvalid() {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return false;
    }
  }
}
