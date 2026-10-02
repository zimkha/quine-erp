package com.zim.identity.domain.valueobject;

import com.zim.identity.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainPasswordTest {

  @Test
  void shouldAcceptTwelveCharacters() {
    assertThat(new PlainPassword("a".repeat(12)).reveal())
        .isEqualTo("a".repeat(12));
  }

  @Test
  void shouldRejectElevenCharacters() {
    assertThatThrownBy(() -> new PlainPassword("a".repeat(11)))
        .isInstanceOf(InvalidValueException.class)
        .extracting("code")
        .isEqualTo("INVALID_PASSWORD");
  }

  @Test
  void shouldRejectAPasswordMadeOnlyOfSpaces() {
    assertThatThrownBy(() -> new PlainPassword(" ".repeat(20)))
        .isInstanceOf(InvalidValueException.class)
        .extracting("code")
        .isEqualTo("INVALID_PASSWORD");
  }

  @Test
  void shouldAcceptExactly72Bytes() {
    assertThat(new PlainPassword("a".repeat(72)).reveal()).hasSize(72);
  }

  @Test
  void shouldRejectMoreThan72BytesEvenWithFewCharacters() {
    // 40 two-byte characters: 40 characters but 80 bytes, past BCrypt's limit.
    String password = "é".repeat(40);

    assertThatThrownBy(() -> new PlainPassword(password))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldNeverPrintItsValueOrAnythingDerivedFromIt() {
    PlainPassword password = new PlainPassword("S3cret-Passw0rd!");

    assertThat(password.toString()).isEqualTo("[PROTECTED]");
    assertThat(password.toString()).doesNotContain("S3cret");
  }

  @Test
  void shouldNeverPrintAPasswordHash() {
    assertThat(new PasswordHash("$2a$10$abcdefghijklmnopqrstuv").toString())
        .isEqualTo("[PROTECTED]");
  }
}
