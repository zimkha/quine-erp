package com.zim.identity.infrastructure.security;

import com.zim.identity.domain.valueobject.PasswordAttempt;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordVerifierTest {

  private static final String PASSWORD = "not-a-real-password-1";

  private final BcryptPasswordHasher bcrypt = new BcryptPasswordHasher();

  @Test
  void shouldAcceptTheRightPassword() {
    PasswordHash hash = bcrypt.hash(new PlainPassword(PASSWORD));

    assertThat(bcrypt.matches(new PasswordAttempt(PASSWORD), hash)).isTrue();
  }

  @Test
  void shouldRefuseAWrongPassword() {
    PasswordHash hash = bcrypt.hash(new PlainPassword(PASSWORD));

    assertThat(bcrypt.matches(new PasswordAttempt("another-fake-pass-2"), hash))
        .isFalse();
  }

  @Test
  void shouldRefuseAShortOrEmptyAttemptWithoutThrowing() {
    PasswordHash hash = bcrypt.hash(new PlainPassword(PASSWORD));

    assertThat(bcrypt.matches(new PasswordAttempt("x"), hash)).isFalse();
    assertThat(bcrypt.matches(new PasswordAttempt(""), hash)).isFalse();
  }

  @Test
  void shouldAcceptAPasswordOfExactly72Bytes() {
    String exactly72 = "a".repeat(72);
    PasswordHash hash = bcrypt.hash(new PlainPassword(exactly72));

    assertThat(bcrypt.matches(new PasswordAttempt(exactly72), hash)).isTrue();
  }

  /**
   * BCryptPasswordEncoder.matches alone would say true here: it ignores
   * everything after byte 72.
   */
  @Test
  void shouldRefuseAnAttemptOver72BytesEvenIfTheFirst72BytesMatch() {
    String exactly72 = "a".repeat(72);
    PasswordHash hash = bcrypt.hash(new PlainPassword(exactly72));

    assertThat(bcrypt.matches(new PasswordAttempt(exactly72 + "b"), hash))
        .isFalse();
  }

  @Test
  void shouldCountBytesNotCharacters() {
    // 36 two-byte characters are 72 bytes; one more is 74.
    String exactly72Bytes = "é".repeat(36);
    PasswordHash hash = bcrypt.hash(new PlainPassword(exactly72Bytes));

    assertThat(bcrypt.matches(new PasswordAttempt(exactly72Bytes), hash)).isTrue();
    assertThat(bcrypt.matches(new PasswordAttempt(exactly72Bytes + "é"), hash))
        .isFalse();
  }

  @Test
  void shouldRefuseAMalformedHashWithoutThrowing() {
    assertThat(bcrypt.matches(
        new PasswordAttempt(PASSWORD), new PasswordHash("not-a-bcrypt-hash")
    )).isFalse();
  }

  @Test
  void shouldProduceAWellFormedHashAtTheDefaultCost() {
    // The dummy hash of the login is made by this same encoder, so it costs
    // what a real hash costs. That the hasher and the verifier are one
    // instance is wired in IdentityConfiguration (a single bcrypt field).
    PasswordHash hash = bcrypt.hash(new PlainPassword(PASSWORD));
    assertThat(hash.value()).startsWith("$2").hasSize(60).contains("$10$");
  }
}
