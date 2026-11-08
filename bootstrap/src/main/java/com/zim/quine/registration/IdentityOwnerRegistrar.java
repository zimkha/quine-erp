package com.zim.quine.registration;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.handler.CreateOwnerHandler;
import com.zim.organization.application.port.OwnerRegistrar;
import com.zim.shared.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Connects registration (organization) to owner creation (identity). Lives in
 * bootstrap because it is the only module that knows both, which never
 * reference each other.
 *
 * <p>It runs inside the registration transaction: {@link CreateOwnerHandler}
 * is wrapped with the default REQUIRED propagation on the same
 * {@code PlatformTransactionManager}, so it joins that transaction. Any
 * exception (e.g. {@code EMAIL_ALREADY_EXISTS}) propagates unchanged and rolls
 * back the organization too.
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
  public void register(
      TenantId tenantId,
      String ownerEmail,
      String ownerPassword
  ) {
    createOwnerHandler.handle(
        new CreateOwnerCommand(tenantId, ownerEmail, ownerPassword)
    );
  }
}
