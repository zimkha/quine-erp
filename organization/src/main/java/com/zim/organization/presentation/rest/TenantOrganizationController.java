package com.zim.organization.presentation.rest;

import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.command.DeactivateStoreCommand;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.handler.DeactivateStoreHandler;
import com.zim.organization.application.result.AddStoreResult;
import com.zim.organization.application.result.ChangeHeadquartersResult;
import com.zim.organization.application.result.DeactivateStoreResult;
import com.zim.organization.presentation.rest.request.AddStoreRequest;
import com.zim.organization.presentation.rest.request.ChangeHeadquartersRequest;
import com.zim.organization.presentation.rest.response.AddStoreResponse;
import com.zim.organization.presentation.rest.response.ChangeHeadquartersResponse;
import com.zim.organization.presentation.rest.response.DeactivateStoreResponse;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Endpoints acting on an existing organization on behalf of the current
 * tenant. The tenant always comes from the {@link CurrentTenantProvider},
 * never from the path, the query string, the body or a header; {@code id}
 * is only the organization id.
 */
@RestController
@RequestMapping("/api/organizations/{id}")
public class TenantOrganizationController {

  private final CurrentTenantProvider currentTenantProvider;
  private final AddStoreHandler addStoreHandler;
  private final ChangeHeadquartersHandler changeHeadquartersHandler;
  private final DeactivateStoreHandler deactivateStoreHandler;

  public TenantOrganizationController(
      CurrentTenantProvider currentTenantProvider,
      AddStoreHandler addStoreHandler,
      ChangeHeadquartersHandler changeHeadquartersHandler,
      DeactivateStoreHandler deactivateStoreHandler
  ) {
    this.currentTenantProvider = Objects.requireNonNull(
        currentTenantProvider,
        "Current tenant provider cannot be null"
    );
    this.addStoreHandler = Objects.requireNonNull(
        addStoreHandler,
        "Add store handler cannot be null"
    );
    this.changeHeadquartersHandler = Objects.requireNonNull(
        changeHeadquartersHandler,
        "Change headquarters handler cannot be null"
    );
    this.deactivateStoreHandler = Objects.requireNonNull(
        deactivateStoreHandler,
        "Deactivate store handler cannot be null"
    );
  }

  @PostMapping("/stores")
  public ResponseEntity<AddStoreResponse> addStore(
      @PathVariable("id") UUID id,
      @Valid @RequestBody AddStoreRequest request
  ) {
    TenantId tenantId = currentTenantProvider.currentTenant();

    AddStoreResult result = addStoreHandler.handle(
        new AddStoreCommand(
            tenantId,
            id,
            request.storeCode(),
            request.storeName()
        )
    );

    AddStoreResponse response = new AddStoreResponse(
        result.organizationId(),
        result.storeId(),
        result.storeCode(),
        result.storeName(),
        result.headquarters(),
        result.active(),
        result.addedAt()
    );

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(response);
  }

  /**
   * Makes another active store of the organization its headquarters. A
   * repeat is answered with 409 {@code STORE_IS_ALREADY_HEADQUARTERS}, which
   * clients treat as "target state already reached".
   */
  @PutMapping("/headquarters")
  public ResponseEntity<ChangeHeadquartersResponse> changeHeadquarters(
      @PathVariable("id") UUID id,
      @Valid @RequestBody ChangeHeadquartersRequest request
  ) {
    TenantId tenantId = currentTenantProvider.currentTenant();

    ChangeHeadquartersResult result = changeHeadquartersHandler.handle(
        new ChangeHeadquartersCommand(
            tenantId,
            id,
            request.storeId()
        )
    );

    ChangeHeadquartersResponse response = new ChangeHeadquartersResponse(
        result.organizationId(),
        result.headquartersId(),
        result.changedAt()
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Deactivates a store of the organization. There is no request body; any
   * body sent is ignored. A repeat is answered with 409
   * {@code STORE_ALREADY_INACTIVE}, which clients treat as "target state
   * already reached".
   *
   * <p>{@code ORGANIZATION_MUST_KEEP_ONE_ACTIVE_STORE} can't be reached
   * here: the headquarters can't be deactivated and an inactive store can't
   * become headquarters, so the headquarters always stays active.
   */
  @PostMapping("/stores/{storeId}/deactivation")
  public ResponseEntity<DeactivateStoreResponse> deactivateStore(
      @PathVariable("id") UUID id,
      @PathVariable("storeId") UUID storeId
  ) {
    TenantId tenantId = currentTenantProvider.currentTenant();

    DeactivateStoreResult result = deactivateStoreHandler.handle(
        new DeactivateStoreCommand(
            tenantId,
            id,
            storeId
        )
    );

    DeactivateStoreResponse response = new DeactivateStoreResponse(
        result.organizationId(),
        result.storeId(),
        result.active(),
        result.deactivatedAt()
    );

    return ResponseEntity.ok(response);
  }
}
