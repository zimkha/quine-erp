package com.zim.identity.domain.valueobject;

import com.zim.identity.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

  @Test
  void shouldTrimAndLowerCase() {
    assertThat(new Email("  Awa.Diop@Example.COM ").value())
        .isEqualTo("awa.diop@example.com");
  }

  @Test
  void shouldTreatDifferentCasesAsEqual() {
    assertThat(new Email("A@B.com")).isEqualTo(new Email("a@b.COM"));
    assertThat(new Email("A@B.com")).hasSameHashCodeAs(new Email("a@b.COM"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", "   ", "plain", "no-at.example.com", "a@b", "a@@b.com",
      "a b@c.com", "@c.com", "a@.com"
  })
  void shouldRejectMalformedAddresses(String value) {
    assertThatThrownBy(() -> new Email(value))
        .isInstanceOf(InvalidValueException.class)
        .extracting("code")
        .isEqualTo("INVALID_EMAIL");
  }

  @Test
  void shouldRejectAnAddressLongerThan254Characters() {
    String tooLong = "a".repeat(250) + "@b.co";

    assertThatThrownBy(() -> new Email(tooLong))
        .isInstanceOf(InvalidValueException.class);
  }
}
