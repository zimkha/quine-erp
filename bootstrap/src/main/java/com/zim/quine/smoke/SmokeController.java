package com.zim.quine.smoke;

import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.presentation.rest.response.ActivateOrganizationResponse;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Local smoke runs only: the activation shortcut Postman's setup uses. */
@RestController
@Profile("smoke")
@RequestMapping("/smoke/organizations/{id}")
class SmokeController {

  private final CurrentTenantProvider currentTenantProvider;
  private final ActivateOrganizationHandler activateOrganizationHandler;

  SmokeController(
      CurrentTenantProvider currentTenantProvider,
      ActivateOrganizationHandler activateOrganizationHandler
  ) {
    this.currentTenantProvider = currentTenantProvider;
    this.activateOrganizationHandler = activateOrganizationHandler;
  }

  @PostMapping("/activate")
  ResponseEntity<ActivateOrganizationResponse> activate(
      @PathVariable("id") UUID id
  ) {
    ActivateOrganizationResult result = activateOrganizationHandler.handle(
        new ActivateOrganizationCommand(
            currentTenantProvider.currentTenant(),
            id
        )
    );
    return ResponseEntity.ok(new ActivateOrganizationResponse(
        result.organizationId(),
        result.status(),
        result.activatedAt()
    ));
  }
}
