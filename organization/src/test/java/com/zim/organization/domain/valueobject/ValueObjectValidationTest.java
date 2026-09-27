package com.zim.organization.domain.valueobject;

import com.zim.organization.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectValidationTest {

  @ParameterizedTest
  @ValueSource(strings = {"XOF", "EUR", "usd", " eur "})
  void shouldAcceptSupportedCurrencies(String value) {
    assertThat(new CurrencyCode(value).value())
        .isIn("XOF", "EUR", "USD");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "ABC", "JPY"})
  void shouldRejectUnsupportedCurrencies(String value) {
    assertThatThrownBy(() -> new CurrencyCode(value))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code()).isEqualTo("UNSUPPORTED_CURRENCY")
        );
  }

  @Test
  void shouldRejectInvalidValuesWithDomainException() {
    assertThatThrownBy(() -> new StoreCode("THIES 01"))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_STORE_CODE")
        );

    assertThatThrownBy(() -> new StoreName("Z"))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_STORE_NAME")
        );

    assertThatThrownBy(() -> new OrganizationName(" X "))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_ORGANIZATION_NAME")
        );

    assertThatThrownBy(() -> new LegalName("Y"))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_LEGAL_NAME")
        );
  }
}
