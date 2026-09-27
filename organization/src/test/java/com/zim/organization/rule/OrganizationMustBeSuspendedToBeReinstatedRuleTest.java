package com.zim.organization.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.rule.OrganizationMustBeSuspendedToBeReinstatedRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMustBeSuspendedToBeReinstatedRuleTest {

  @Test
  void shouldNotBeBrokenWhenSuspended() {
    OrganizationMustBeSuspendedToBeReinstatedRule rule =
        new OrganizationMustBeSuspendedToBeReinstatedRule(
            OrganizationStatus.SUSPENDED
        );

    assertThat(rule.isBroken()).isFalse();
  }

  @Test
  void shouldBeBrokenAsAlreadyActiveWhenActive() {
    OrganizationMustBeSuspendedToBeReinstatedRule rule =
        new OrganizationMustBeSuspendedToBeReinstatedRule(
            OrganizationStatus.ACTIVE
        );

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_ALREADY_ACTIVE");
    assertThat(rule.message()).isEqualTo("Organization is already active");
  }

  @Test
  void shouldBeBrokenWhenPendingActivation() {
    OrganizationMustBeSuspendedToBeReinstatedRule rule =
        new OrganizationMustBeSuspendedToBeReinstatedRule(
            OrganizationStatus.PENDING_ACTIVATION
        );

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_CANNOT_BE_REINSTATED");
    assertThat(rule.message()).isEqualTo(
        "Organization cannot be reinstated from status 'PENDING_ACTIVATION'"
    );
  }

  @Test
  void shouldBeBrokenWhenClosed() {
    OrganizationMustBeSuspendedToBeReinstatedRule rule =
        new OrganizationMustBeSuspendedToBeReinstatedRule(
            OrganizationStatus.CLOSED
        );

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_CANNOT_BE_REINSTATED");
    assertThat(rule.message()).isEqualTo(
        "Organization cannot be reinstated from status 'CLOSED'"
    );
  }

  @Test
  void shouldRejectNullStatus() {
    assertThatThrownBy(
        () -> new OrganizationMustBeSuspendedToBeReinstatedRule(null)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Current status cannot be null");
  }
}
