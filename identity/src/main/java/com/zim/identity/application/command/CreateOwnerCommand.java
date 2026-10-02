package com.zim.identity.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;

/** The password is a raw string here, so it is masked in {@link #toString()}. */
public record CreateOwnerCommand(
    TenantId tenantId,
    String email,
    String password
) {

  public CreateOwnerCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(email, "E-mail cannot be null");
    Objects.requireNonNull(password, "Password cannot be null");
  }

  @Override
  public String toString() {
    return "CreateOwnerCommand[tenantId=" + tenantId
        + ", email=" + email + ", password=[PROTECTED]]";
  }
}
