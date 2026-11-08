package com.zim.organization.presentation.rest.request;

import org.junit.jupiter.api.Test;

import static com.zim.shared.web.testing.RequestDtoConstraintGuard.assertOnlyPinnedConstraints;

/**
 * Every constraint on a request DTO must have a pinned English text in
 * {@code FieldErrorMessages} (web-shared, T5f). An unpinned constraint
 * would still be reported, but only as the generic "is invalid". The logic
 * is shared: {@code RequestDtoConstraintGuard} in the web-shared test-jar.
 */
class RequestDtoConstraintGuardTest {

  private static final String REQUEST_PACKAGE =
      "com.zim.organization.presentation.rest.request";

  @Test
  void shouldOnlyUseConstraintsWithPinnedTexts() {
    // The scan also sees test classes in the same package.
    assertOnlyPinnedConstraints(
        REQUEST_PACKAGE,
        RequestDtoConstraintGuardTest.class
    );
  }
}
