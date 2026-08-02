package com.zim.organization.application.handler;

import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.port.ClockProvider;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.EventIdGenerator;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.domain.model.Organization;

import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ActivateOrganizationHandler {

    private final OrganizationRepository organizationRepository;
    private final EventIdGenerator eventIdGenerator;
    private final ClockProvider clockProvider;
    private final DomainEventPublisher domainEventPublisher;

    public ActivateOrganizationHandler(
            OrganizationRepository organizationRepository,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        this.organizationRepository = Objects.requireNonNull(
                organizationRepository,
                "Organization repository cannot be null"
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

    public ActivateOrganizationResult handle(
            ActivateOrganizationCommand command
    ) {
        Objects.requireNonNull(command, "Command cannot be null");

        OrganizationId organizationId =
                new OrganizationId(command.organizationId());

        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(
                        () -> new OrganizationNotFoundException(
                                command.organizationId()
                        )
                );

        UUID eventId = Objects.requireNonNull(
                eventIdGenerator.generate(),
                "Generated event id cannot be null"
        );

        Instant activatedAt = Objects.requireNonNull(
                clockProvider.now(),
                "Current time cannot be null"
        );

        organization.activate(eventId, activatedAt);

        Organization savedOrganization =
                organizationRepository.save(organization);

        domainEventPublisher.publish(
                savedOrganization.pullDomainEvents()
        );

        return new ActivateOrganizationResult(
                savedOrganization.id().value(),
                savedOrganization.tenantId().value(),
                savedOrganization.status().name(),
                activatedAt
        );
    }
}