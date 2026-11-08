package com.zim.identity.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordAttemptTest {

  @Test
  void shouldNeverPrintItsValue() {
    PasswordAttempt attempt = new PasswordAttempt("not-a-real-password-1");

    assertThat(attempt.toString()).isEqualTo("[PROTECTED]");
    assertThat(attempt.toString()).doesNotContain("not-a-real");
  }

  @Test
  void shouldApplyNoLengthPolicy() {
    assertThat(new PasswordAttempt("x").reveal()).isEqualTo("x");
    assertThat(new PasswordAttempt("a".repeat(500)).reveal()).hasSize(500);
    assertThat(new PasswordAttempt("  ").reveal()).isEqualTo("  ");
  }

  @Test
  void shouldRejectNull() {
    assertThatThrownBy(() -> new PasswordAttempt(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldHaveNoCustomEquality() {
    assertThat(new PasswordAttempt("same-value-123"))
        .isNotEqualTo(new PasswordAttempt("same-value-123"));
  }
}
