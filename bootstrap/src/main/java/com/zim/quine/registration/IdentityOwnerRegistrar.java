package com.zim.quine.registration;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.handler.CreateOwnerHandler;
import com.zim.organization.application.port.OwnerRegistrar;
import com.zim.shared.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Connects the two modules, which never reference each other: registration
 * (organization) asks for an owner, and identity creates it. The handler
 * joins the registration transaction, so any failure here, such as an
 * e-mail already taken, rolls the organization back too.
 */
@Component
class IdentityOwnerRegistrar implements OwnerRegistrar {

  private final CreateOwnerHandler createOwnerHandler;

  IdentityOwnerRegistrar(CreateOwnerHandler createOwnerHandler) {
    this.createOwnerHandler = Objects.requireNonNull(
        createOwnerHandler,
        "Create owner handler cannot be null"
    );
  }

  @Override
  public void register(TenantId tenantId, String email, String password) {
    createOwnerHandler.handle(new CreateOwnerCommand(tenantId, email, password));
  }
}
