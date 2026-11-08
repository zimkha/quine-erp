package com.zim.identity.presentation.rest.request;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.zim.shared.web.testing.RequestDtoConstraintGuard.assertOnlyPinnedConstraints;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every constraint on a request DTO must have a pinned English text in
 * {@code FieldErrorMessages} (web-shared). The logic is shared in the
 * web-shared test-jar.
 */
class RequestDtoConstraintGuardTest {

  private static final String REQUEST_PACKAGE =
      "com.zim.identity.presentation.rest.request";

  @Test
  void shouldOnlyUseConstraintsWithPinnedTexts() {
    // The scan also sees test classes in the same package.
    assertOnlyPinnedConstraints(REQUEST_PACKAGE, RequestDtoConstraintGuardTest.class);
  }

  /**
   * Spring MVC logs a rejected value at DEBUG, so the password field may
   * carry only {@code @NotBlank}; the e-mail has no {@code @Email} (a
   * malformed address is a 401, not a 400).
   */
  @Test
  void shouldKeepTheLoginDtoThin() throws Exception {
    assertThat(constraintsOf("password")).containsExactly("NotBlank");
    assertThat(constraintsOf("email")).containsExactlyInAnyOrder("NotBlank", "Size");
  }

  private static List<String> constraintsOf(String component) throws Exception {
    return Arrays.stream(LoginRequest.class.getDeclaredField(component).getAnnotations())
        .map(annotation -> annotation.annotationType().getSimpleName())
        .toList();
  }
}
