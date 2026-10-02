package com.zim.identity.infrastructure.security;

import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {

  private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

  @Test
  void shouldProduceAHashThatMatchesThePasswordOnly() {
    PasswordHash hash = hasher.hash(new PlainPassword("S3cret-Passw0rd!"));
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    assertThat(hash.value()).startsWith("$2");
    assertThat(encoder.matches("S3cret-Passw0rd!", hash.value())).isTrue();
    assertThat(encoder.matches("another-passw0rd!", hash.value())).isFalse();
  }

  @Test
  void shouldNotContainThePassword() {
    PasswordHash hash = hasher.hash(new PlainPassword("S3cret-Passw0rd!"));

    assertThat(hash.value()).doesNotContain("S3cret");
  }

  @Test
  void shouldHashTheSamePasswordDifferentlyEachTime() {
    PlainPassword password = new PlainPassword("S3cret-Passw0rd!");

    assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
  }
}
