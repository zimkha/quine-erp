package com.zim.shared.web.testing;

import com.zim.shared.web.FieldErrorMessages;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.BeanDescriptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every constraint on a request DTO must have a pinned English text in
 * {@link FieldErrorMessages} (T5f), and a DTO has no class-level constraint.
 * An unpinned constraint would still be reported, but only as the generic
 * "is invalid"; a class-level one is not reported as a field error at all.
 *
 * <p>A module calls {@link #assertOnlyPinnedConstraints} from its own
 * guard test with the package of its request DTOs.
 */
public final class RequestDtoConstraintGuard {

  private static final String HOW_TO_FIX =
      "has no pinned text: add it to "
          + "com.zim.shared.web.FieldErrorMessages (web-shared) and a row to "
          + "the Pinned texts table of "
          + "docs/tickets/validation-message-locale.md";

  private RequestDtoConstraintGuard() {
  }

  /**
   * Scans {@code requestPackage} (the scan also sees test classes of the
   * same package, so the caller lists them in {@code ignored}) and checks
   * every class found.
   */
  public static void assertOnlyPinnedConstraints(
      String requestPackage,
      Class<?>... ignored
  ) {
    List<Class<?>> dtos = scan(requestPackage, ignored);

    assertThat(dtos)
        .as("no request DTO found in %s", requestPackage)
        .isNotEmpty();
    assertOnlyPinnedConstraints(dtos);
  }

  public static void assertOnlyPinnedConstraints(Collection<Class<?>> dtos) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      for (Class<?> dto : dtos) {
        BeanDescriptor descriptor = validator.getConstraintsForClass(dto);

        assertThat(descriptor.getConstraintDescriptors())
            .as("%s: class-level constraints are not reported as field "
                + "errors, so the 400 message would be empty", dto.getName())
            .isEmpty();

        descriptor.getConstrainedProperties().forEach(property ->
            property.getConstraintDescriptors().forEach(constraint -> {
              Class<? extends Annotation> type =
                  constraint.getAnnotation().annotationType();
              assertThat(FieldErrorMessages.PINNED_CONSTRAINTS)
                  .as("@%s on %s.%s %s", type.getName(), dto.getSimpleName(),
                      property.getPropertyName(), HOW_TO_FIX)
                  .contains(type);
            }));
      }
    }
  }

  private static List<Class<?>> scan(
      String requestPackage,
      Class<?>[] ignored
  ) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((reader, factory) -> true);

    List<Class<?>> ignoredTypes = List.of(ignored);
    return scanner.findCandidateComponents(requestPackage)
        .stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(name -> ClassUtils.resolveClassName(
            name,
            RequestDtoConstraintGuard.class.getClassLoader()
        ))
        .filter(type -> !ignoredTypes.contains(type))
        .toList();
  }
}
