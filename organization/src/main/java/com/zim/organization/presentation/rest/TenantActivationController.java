package com.zim.organization.presentation.rest;

import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.presentation.rest.response.ActivateOrganizationResponse;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant self-activation, a transitional route. Registration is public and
 * login is real, so with this route anyone can register, log in and activate
 * their own organization, skipping the platform's validation (T7 PO-1).
 * It is therefore <strong>off by default</strong>: without
 * {@code quine.organization.tenant-activation.enabled=true} the route does
 * not exist. Enable it only for local or team-only use (docker-compose and
 * the tests that exercise it); never in an environment reachable by anyone
 * outside the team. T9d deletes this whole class and the flag.
 */
@RestController
@RequestMapping("/api/organizations/{id}")
@ConditionalOnProperty(
    name = TenantActivationController.ENABLED_PROPERTY,
    havingValue = "true"
)
public class TenantActivationController {

  public static final String ENABLED_PROPERTY =
      "quine.organization.tenant-activation.enabled";

  private final CurrentTenantProvider currentTenantProvider;
  private final ActivateOrganizationHandler activateOrganizationHandler;

  public TenantActivationController(
      CurrentTenantProvider currentTenantProvider,
      ActivateOrganizationHandler activateOrganizationHandler
  ) {
    this.currentTenantProvider = Objects.requireNonNull(
        currentTenantProvider,
        "Current tenant provider cannot be null"
    );
    this.activateOrganizationHandler = Objects.requireNonNull(
        activateOrganizationHandler,
        "Activate organization handler cannot be null"
    );
  }

  /**
   * Activates the tenant's newly registered organization. There is no
   * request body; any body sent is ignored. Only a
   * {@code PENDING_ACTIVATION} organization can be activated: a repeat is
   * answered with 409 {@code ORGANIZATION_ALREADY_ACTIVE}, which clients
   * treat as "target state already reached", and a {@code SUSPENDED} or
   * {@code CLOSED} one with 409 {@code ORGANIZATION_CANNOT_BE_ACTIVATED}.
   * The response does not echo the tenant.
   */
  @PostMapping("/activation")
  public ResponseEntity<ActivateOrganizationResponse> activateOrganization(
      @PathVariable("id") UUID id
  ) {
    TenantId tenantId = currentTenantProvider.currentTenant();

    ActivateOrganizationResult result = activateOrganizationHandler.handle(
        new ActivateOrganizationCommand(tenantId, id)
    );

    ActivateOrganizationResponse response = new ActivateOrganizationResponse(
        result.organizationId(),
        result.status(),
        result.activatedAt()
    );

    return ResponseEntity.ok(response);
  }
}
