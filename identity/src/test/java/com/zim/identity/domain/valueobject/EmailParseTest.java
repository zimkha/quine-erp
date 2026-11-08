package com.zim.identity.domain.valueobject;

import com.zim.identity.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailParseTest {

  @Test
  void shouldTrimAndLowerCaseLikeTheConstructor() {
    assertThat(Email.parse("  Awa.Diop@Example.COM "))
        .contains(new Email("awa.diop@example.com"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", "   ", "plain", "no-at.example.com", "a@b", "a@@b.com",
      "a b@c.com", "@c.com", "a@.com", "a@b..com", "a@-b.com.",
      "é@b.com", "a@é.com", "İ@b.com", "a\tb@c.com"
  })
  void shouldReturnEmptyWhereTheConstructorThrows(String value) {
    assertThat(Email.parse(value)).isEmpty();
    assertThatThrownBy(() -> new Email(value))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldReturnEmptyForAnAddressLongerThan254Characters() {
    assertThat(Email.parse("a".repeat(250) + "@b.co")).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullWhileTheConstructorKeepsItsNullCheck() {
    assertThat(Email.parse(null)).isEmpty();
    assertThatThrownBy(() -> new Email(null))
        .isInstanceOf(NullPointerException.class);
  }
}
