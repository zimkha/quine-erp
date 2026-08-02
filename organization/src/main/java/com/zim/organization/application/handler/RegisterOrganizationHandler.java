package com.zim.organization.application.handler;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.port.*;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.*;


import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class RegisterOrganizationHandler {

    private final OrganizationRepository organizationRepository;
    private final OrganizationIdGenerator organizationIdGenerator;
    private final TenantIdGenerator tenantIdGenerator;
    private final StoreIdGenerator storeIdGenerator;
    private final EventIdGenerator eventIdGenerator;
    private final ClockProvider clockProvider;
    private final DomainEventPublisher domainEventPublisher;

    public RegisterOrganizationHandler(
            OrganizationRepository organizationRepository,
            OrganizationIdGenerator organizationIdGenerator,
            TenantIdGenerator tenantIdGenerator,
            StoreIdGenerator storeIdGenerator,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider, DomainEventPublisher domainEventPublisher
    ) {
        this.organizationRepository =
                Objects.requireNonNull(organizationRepository);
        this.organizationIdGenerator =
                Objects.requireNonNull(organizationIdGenerator);
        this.tenantIdGenerator =
                Objects.requireNonNull(tenantIdGenerator);
        this.storeIdGenerator =
                Objects.requireNonNull(storeIdGenerator);
        this.eventIdGenerator =
                Objects.requireNonNull(eventIdGenerator);
        this.clockProvider =
                Objects.requireNonNull(clockProvider);
        this.domainEventPublisher = domainEventPublisher;
    }
    public RegisterOrganizationResult handle(
            RegisterOrganizationCommand command
    ) {
        Objects.requireNonNull(command, "Command cannot be null");

        String normalizedLegalName =
                normalizeLegalName(command.legalName());

        if (organizationRepository.existsByLegalName(normalizedLegalName)) {
            throw new OrganizationAlreadyExistsException(
                    normalizedLegalName
            );
        }

        OrganizationId organizationId =
                organizationIdGenerator.generate();

        TenantId tenantId =
                tenantIdGenerator.generate();

        StoreId headquartersId =
                storeIdGenerator.generate();

        Instant createdAt =
                clockProvider.now();
        UUID eventId = eventIdGenerator.generate();

        Organization organization = Organization.register(
                organizationId,
                tenantId,
                new OrganizationName(command.organizationName()),
                normalizedLegalName,
                new CurrencyCode(command.currencyCode()),
                headquartersId,
                new StoreCode(command.headquartersCode()),
                new StoreName(command.headquartersName()),
                eventId,
                createdAt
        );

        Organization savedOrganization =
                organizationRepository.save(organization);

        domainEventPublisher.publish(
                savedOrganization.pullDomainEvents()
        );
        return new RegisterOrganizationResult(
                savedOrganization.id().value(),
                savedOrganization.tenantId().value(),
                savedOrganization.name().value(),
                savedOrganization.status().name(),
                headquartersId.value(),
                savedOrganization.createdAt()
        );
    }

    private static String normalizeLegalName(String legalName) {
        Objects.requireNonNull(
                legalName,
                "Legal name cannot be null"
        );

        return legalName
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
