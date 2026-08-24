package com.zim.organization.application.handler;

import com.zim.organization.application.command.CloseOrganizationCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.port.ClockProvider;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.EventIdGenerator;
import com.zim.organization.application.result.CloseOrganizationResult;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CloseOrganizationHandler {

    private final OrganizationRepository organizationRepository;
    private final EventIdGenerator eventIdGenerator;
    private final ClockProvider clockProvider;
    private final DomainEventPublisher domainEventPublisher;

    public CloseOrganizationHandler(
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

    public CloseOrganizationResult handle(
            CloseOrganizationCommand command
    ) {
        Objects.requireNonNull(command, "Command cannot be null");

        Organization organization = organizationRepository
                .findById(
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

        Instant closedAt = Objects.requireNonNull(
                clockProvider.now(),
                "Current time cannot be null"
        );

        organization.close(eventId, closedAt);


                organizationRepository.save(organization);

        domainEventPublisher.publish(
                organization.pullDomainEvents()
        );

        return new CloseOrganizationResult(
                organization.id().value(),
                organization.tenantId().value(),
                organization.status().name(),
                closedAt
        );
    }
}