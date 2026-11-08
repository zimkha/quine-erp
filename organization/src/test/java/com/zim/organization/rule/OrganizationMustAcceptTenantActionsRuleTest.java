package com.zim.organization.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.rule.OrganizationMustAcceptTenantActionsRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMustAcceptTenantActionsRuleTest {

  @Test
  void shouldNotBeBrokenWhenActive() {
    assertThat(
        new OrganizationMustAcceptTenantActionsRule(
            OrganizationStatus.ACTIVE
        ).isBroken()
    ).isFalse();
  }

  /**
   * CLOSED is not caught here: the action-specific rules keep producing
   * their generic codes (and ORGANIZATION_ALREADY_CLOSED for closure).
   */
  @Test
  void shouldNotBeBrokenWhenClosed() {
    assertThat(
        new OrganizationMustAcceptTenantActionsRule(
            OrganizationStatus.CLOSED
        ).isBroken()
    ).isFalse();
  }

  @Test
  void shouldBeBrokenWithPendingValidationWhenPendingActivation() {
    OrganizationMustAcceptTenantActionsRule rule =
        new OrganizationMustAcceptTenantActionsRule(
            OrganizationStatus.PENDING_ACTIVATION
        );

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_PENDING_VALIDATION");
    assertThat(rule.message())
        .isEqualTo("Organization is waiting for validation by the platform");
  }

  @Test
  void shouldBeBrokenWithOrganizationSuspendedWhenSuspended() {
    OrganizationMustAcceptTenantActionsRule rule =
        new OrganizationMustAcceptTenantActionsRule(
            OrganizationStatus.SUSPENDED
        );

    assertThat(rule.isBroken()).isTrue();
    assertThat(rule.code()).isEqualTo("ORGANIZATION_SUSPENDED");
    assertThat(rule.message())
        .isEqualTo("Organization is suspended by the platform");
  }

  /**
   * D15: the ORGANIZATION_<STATE> family are refusals tied to the current
   * state, never "target state already reached". No code of the family may
   * start with ORGANIZATION_ALREADY_ (the "already" list of
   * endpoints-T5.md). The code base exposes no such list as a constant, so
   * this is the guard.
   */
  @ParameterizedTest
  @EnumSource(
      value = OrganizationStatus.class,
      names = {"PENDING_ACTIVATION", "SUSPENDED"}
  )
  void shouldNeverUseTheAlreadyFamily(OrganizationStatus status) {
    OrganizationMustAcceptTenantActionsRule rule =
        new OrganizationMustAcceptTenantActionsRule(status);

    assertThat(rule.code())
        .startsWith("ORGANIZATION_")
        .doesNotContain("ALREADY")
        .doesNotContain("CANNOT_BE");
  }

  @Test
  void shouldRejectNullStatus() {
    assertThatThrownBy(
        () -> new OrganizationMustAcceptTenantActionsRule(null)
    )
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Current status cannot be null");
  }
}
