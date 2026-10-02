package com.zim.identity.domain.rule;

import com.zim.shared.domain.BusinessRule;

/** A tenant has exactly one owner at first (PO decision 3). */
public record TenantMustHaveSingleOwnerRule(
    boolean tenantAlreadyHasOwner
) implements BusinessRule {

  @Override
  public boolean isBroken() {
    return tenantAlreadyHasOwner;
  }

  @Override
  public String code() {
    return "TENANT_ALREADY_HAS_OWNER";
  }

  @Override
  public String message() {
    return "The tenant already has an owner";
  }
}
