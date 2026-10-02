package com.zim.identity.domain.rule;

import com.zim.identity.domain.valueobject.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {

  @Test
  void emailRuleIsBrokenOnlyWhenTheAddressIsTaken() {
    Email email = new Email("a@b.com");

    assertThat(new EmailMustBeUniqueRule(email, true).isBroken()).isTrue();
    assertThat(new EmailMustBeUniqueRule(email, false).isBroken()).isFalse();
  }

  @Test
  void emailRuleHasAStableCodeAndDoesNotEchoTheAddress() {
    EmailMustBeUniqueRule rule =
        new EmailMustBeUniqueRule(new Email("secret@b.com"), true);

    assertThat(rule.code()).isEqualTo("EMAIL_ALREADY_EXISTS");
    assertThat(rule.message()).doesNotContain("secret@b.com");
  }

  @Test
  void ownerRuleIsBrokenOnlyWhenTheTenantAlreadyHasAnOwner() {
    assertThat(new TenantMustHaveSingleOwnerRule(true).isBroken()).isTrue();
    assertThat(new TenantMustHaveSingleOwnerRule(false).isBroken()).isFalse();
    assertThat(new TenantMustHaveSingleOwnerRule(true).code())
        .isEqualTo("TENANT_ALREADY_HAS_OWNER");
  }
}
