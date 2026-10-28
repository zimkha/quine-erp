package com.zim.organization.presentation.rest.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.BeanDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every constraint on a request DTO must have a pinned English text in
 * {@code FieldErrorMessages} (T5f). An unpinned constraint would still be
 * reported, but only as the generic "is invalid".
 */
class RequestDtoConstraintGuardTest {

  private static final String REQUEST_PACKAGE =
      "com.zim.organization.presentation.rest.request";

  private static final Set<Class<? extends Annotation>> PINNED =
      Set.of(NotBlank.class, NotNull.class, Size.class, Pattern.class,
          Email.class);

  private static final String HOW_TO_FIX =
      "has no pinned text: add it to FieldErrorMessages and a row to the "
          + "Pinned texts table of docs/tickets/validation-message-locale.md";

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
  void shouldOnlyUseConstraintsWithPinnedTexts() {
    Validator validator = validatorFactory.getValidator();

    for (Class<?> dto : requestDtos()) {
      BeanDescriptor descriptor = validator.getConstraintsForClass(dto);

      assertThat(descriptor.getConstraintDescriptors())
          .as("%s: class-level constraints are not reported as field "
              + "errors, so the 400 message would be empty", dto.getName())
          .isEmpty();

      descriptor.getConstrainedProperties().forEach(property ->
          property.getConstraintDescriptors().forEach(constraint -> {
            Class<? extends Annotation> type =
                constraint.getAnnotation().annotationType();
            assertThat(PINNED)
                .as("@%s on %s.%s %s", type.getName(), dto.getSimpleName(),
                    property.getPropertyName(), HOW_TO_FIX)
                .contains(type);
          }));
    }
  }

  /**
   * The password is a raw secret: no request DTO may print it, so a stray
   * log line or error message cannot leak it.
   */
  @Test
  void shouldNeverPrintThePasswordOfTheRegistrationRequest() {
    RegisterOrganizationRequest request = new RegisterOrganizationRequest(
        "Quincaillerie Thiès", "Quincaillerie Thiès SARL", "XOF",
        "THIES-01", "Magasin principal", "owner@example.com",
        "placeholder-owner-value");

    assertThat(request.toString())
        .doesNotContain("placeholder-owner-value")
        .contains("[PROTECTED]");
  }

  /** The e-mail is capped and the password bounded (bcrypt reads 72 bytes). */
  @Test
  void shouldBoundTheOwnerFieldsOfTheRegistrationRequest() throws Exception {
    Size email = RegisterOrganizationRequest.class
        .getDeclaredField("ownerEmail").getAnnotation(Size.class);
    Size password = RegisterOrganizationRequest.class
        .getDeclaredField("ownerPassword").getAnnotation(Size.class);

    assertThat(email.max()).isEqualTo(254);
    assertThat(password.min()).isEqualTo(12);
    assertThat(password.max()).isEqualTo(72);
  }

  private static List<Class<?>> requestDtos() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((reader, factory) -> true);

    List<Class<?>> dtos = scanner.findCandidateComponents(REQUEST_PACKAGE)
        .stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(name -> ClassUtils.resolveClassName(
            name,
            RequestDtoConstraintGuardTest.class.getClassLoader()
        ))
        // The scan also sees test classes in the same package.
        .filter(type -> !type.equals(RequestDtoConstraintGuardTest.class))
        .toList();

    assertThat(dtos)
        .as("no request DTO found in %s", REQUEST_PACKAGE)
        .isNotEmpty();
    return dtos;
  }
}
