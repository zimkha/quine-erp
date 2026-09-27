package com.zim.organization.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.rule.OrganizationMustBeActivatableRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMustBeActivatableRuleTest {

  @Test
  void shouldNotBeBrokenWhenPendingActivation() {
    OrganizationMustBeActivatableRule rule =
        new OrganizationMustBeActivatableRule(
            OrganizationStatus.PENDING_ACTIVATION
        );

    assertThat(rule.isBroken()).isFalse();
  }

  @Test
  void shouldBeBrokenAsAlreadyActiveWhenActive() {
    OrganizationMustBeActivatableRule rule =
        new OrganizationMustBeActivatableRule(OrganizationStatus.ACTIVE);

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_ALREADY_ACTIVE");
    assertThat(rule.message()).isEqualTo("Organization is already active");
  }

  @Test
  void shouldBeBrokenWhenSuspended() {
    OrganizationMustBeActivatableRule rule =
        new OrganizationMustBeActivatableRule(OrganizationStatus.SUSPENDED);

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_CANNOT_BE_ACTIVATED");
    assertThat(rule.message()).isEqualTo(
        "Organization cannot be activated from status 'SUSPENDED'"
    );
  }

  @Test
  void shouldBeBrokenWhenClosed() {
    OrganizationMustBeActivatableRule rule =
        new OrganizationMustBeActivatableRule(OrganizationStatus.CLOSED);

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_CANNOT_BE_ACTIVATED");
    assertThat(rule.message()).isEqualTo(
        "Organization cannot be activated from status 'CLOSED'"
    );
  }

  @Test
  void shouldRejectNullStatus() {
    assertThatThrownBy(() -> new OrganizationMustBeActivatableRule(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Current status cannot be null");
  }
}
