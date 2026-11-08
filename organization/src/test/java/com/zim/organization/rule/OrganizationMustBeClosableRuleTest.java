package com.zim.organization.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.rule.OrganizationMustBeClosableRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMustBeClosableRuleTest {

  @Test
  void shouldNotBeBrokenWhenActive() {
    assertThat(
        new OrganizationMustBeClosableRule(OrganizationStatus.ACTIVE)
            .isBroken()
    ).isFalse();
  }

  @Test
  void shouldBeBrokenAsAlreadyClosedWhenClosed() {
    OrganizationMustBeClosableRule rule =
        new OrganizationMustBeClosableRule(OrganizationStatus.CLOSED);

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_ALREADY_CLOSED");
    assertThat(rule.message()).isEqualTo("Organization is already closed");
  }

  /** PO-1 (T9g): SUSPENDED is no longer closable. */
  @ParameterizedTest
  @EnumSource(
      value = OrganizationStatus.class,
      names = {"PENDING_ACTIVATION", "SUSPENDED"}
  )
  void shouldBeBrokenAsCannotBeClosedFromOtherStatuses(
      OrganizationStatus status
  ) {
    OrganizationMustBeClosableRule rule =
        new OrganizationMustBeClosableRule(status);

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_CANNOT_BE_CLOSED");
    assertThat(rule.message()).isEqualTo(
        "Organization cannot be closed from status '%s'".formatted(status)
    );
  }

  @Test
  void shouldRejectNullStatus() {
    assertThatThrownBy(() -> new OrganizationMustBeClosableRule(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Current status cannot be null");
  }
}
