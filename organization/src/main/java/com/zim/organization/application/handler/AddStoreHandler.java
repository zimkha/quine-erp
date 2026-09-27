package com.zim.organization.application.handler;

import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.port.ClockProvider;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.EventIdGenerator;
import com.zim.organization.application.port.StoreIdGenerator;
import com.zim.organization.application.result.AddStoreResult;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AddStoreHandler {

  private final OrganizationRepository organizationRepository;
  private final StoreIdGenerator storeIdGenerator;
  private final EventIdGenerator eventIdGenerator;
  private final ClockProvider clockProvider;
  private final DomainEventPublisher domainEventPublisher;

  public AddStoreHandler(
      OrganizationRepository organizationRepository,
      StoreIdGenerator storeIdGenerator,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher
  ) {
    this.organizationRepository = Objects.requireNonNull(
        organizationRepository,
        "Organization repository cannot be null"
    );
    this.storeIdGenerator = Objects.requireNonNull(
        storeIdGenerator,
        "Store id generator cannot be null"
    );
    this.eventIdGenerator = Objects.requireNonNull(
        eventIdGenerator,
        "Event id generator cannot be null"
    );
    this.clockProvider = Objects.requireNonNull(
        clockProvider,
        "Clock provider cannot be null"
    );
    this.domainEventPublisher = Objects.requireNonNull(
        domainEventPublisher,
        "Domain event publisher cannot be null"
    );
  }

  public AddStoreResult handle(AddStoreCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    OrganizationId organizationId =
        new OrganizationId(command.organizationId());

    Organization organization = organizationRepository
        .findById(command.tenantId(), organizationId)
        .orElseThrow(
            () -> new OrganizationNotFoundException(
                command.organizationId()
            )
        );

    StoreId storeId = Objects.requireNonNull(
        storeIdGenerator.generate(),
        "Generated store id cannot be null"
    );

    UUID eventId = Objects.requireNonNull(
        eventIdGenerator.generate(),
        "Generated event id cannot be null"
    );

    Instant addedAt = Objects.requireNonNull(
        clockProvider.now(),
        "Current time cannot be null"
    );

    StoreCode storeCode = new StoreCode(command.storeCode());
    StoreName storeName = new StoreName(command.storeName());

    organization.addStore(
        storeId,
        storeCode,
        storeName,
        eventId,
        addedAt
    );

        organizationRepository.save(organization);

    domainEventPublisher.publish(
        organization.pullDomainEvents()
    );

    Store addedStore = organization.stores()
        .stream()
        .filter(store -> store.id().equals(storeId))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException(
                "Added store was not found in organization"
            )
        );

    return new AddStoreResult(
        organization.id().value(),
        addedStore.id().value(),
        addedStore.code().value(),
        addedStore.name().value(),
        addedStore.isHeadquarters(),
        addedStore.isActive(),
        addedAt
    );
  }
}