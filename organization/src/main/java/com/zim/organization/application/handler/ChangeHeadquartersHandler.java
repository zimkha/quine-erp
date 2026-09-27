package com.zim.organization.application.handler;

import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.port.ClockProvider;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.EventIdGenerator;
import com.zim.organization.application.result.ChangeHeadquartersResult;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ChangeHeadquartersHandler {

  private final OrganizationRepository organizationRepository;
  private final EventIdGenerator eventIdGenerator;
  private final ClockProvider clockProvider;
  private final DomainEventPublisher domainEventPublisher;

  public ChangeHeadquartersHandler(
      OrganizationRepository organizationRepository,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher
  ) {
    this.organizationRepository = Objects.requireNonNull(
        organizationRepository
    );
    this.eventIdGenerator = Objects.requireNonNull(
        eventIdGenerator
    );
    this.clockProvider = Objects.requireNonNull(
        clockProvider
    );
    this.domainEventPublisher = Objects.requireNonNull(
        domainEventPublisher
    );
  }

  public ChangeHeadquartersResult handle(
      ChangeHeadquartersCommand command
  ) {
    Objects.requireNonNull(command, "Command cannot be null");

    Organization organization = organizationRepository
        .findById(
            command.tenantId(),
            new OrganizationId(command.organizationId())
        )
        .orElseThrow(() ->
            new OrganizationNotFoundException(
                command.organizationId()
            )
        );

    UUID eventId = Objects.requireNonNull(
        eventIdGenerator.generate(),
        "Generated event id cannot be null"
    );

    Instant changedAt = Objects.requireNonNull(
        clockProvider.now(),
        "Current time cannot be null"
    );

    StoreId newHeadquartersId =
        new StoreId(command.newHeadquartersId());

    organization.changeHeadquarters(
        newHeadquartersId,
        eventId,
        changedAt
    );


        organizationRepository.save(organization);

    domainEventPublisher.publish(
        organization.pullDomainEvents()
    );

    return new ChangeHeadquartersResult(
        organization.id().value(),
        newHeadquartersId.value(),
        changedAt
    );
  }
}