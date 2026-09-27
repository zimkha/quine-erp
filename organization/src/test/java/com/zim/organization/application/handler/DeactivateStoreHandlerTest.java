package com.zim.organization.application.handler;

import com.zim.organization.application.command.DeactivateStoreCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.result.DeactivateStoreResult;

import com.zim.organization.domain.event.StoreDeactivated;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.TenantId;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeactivateStoreHandlerTest {

    private static final UUID ORGANIZATION_UUID =
            UUID.fromString(
                    "5c80d578-83f7-4b44-b5f7-598530067a09"
            );

    private static final UUID TENANT_UUID =
            UUID.fromString(
                    "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
            );

    private static final UUID HEADQUARTERS_UUID =
            UUID.fromString(
                    "4ee0d038-4617-435c-b7c8-48697d4cf909"
            );

    private static final UUID SECONDARY_STORE_UUID =
            UUID.fromString(
                    "86fd6eb4-23f6-4381-842e-e5d57def4a39"
            );

    private static final UUID EVENT_UUID =
            UUID.fromString(
                    "4bd292f0-d945-43dd-982c-ddd6b966bf17"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-01T10:00:00Z");

    private static final Instant DEACTIVATED_AT =
            Instant.parse("2026-08-02T12:00:00Z");

    private InMemoryOrganizationRepository repository;
    private InMemoryDomainEventPublisher publisher;
    private DeactivateStoreHandler handler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
        publisher = new InMemoryDomainEventPublisher();

        handler = new DeactivateStoreHandler(
                repository,
                () -> EVENT_UUID,
                () -> DEACTIVATED_AT,
                publisher
        );
    }

    @Test
    void shouldDeactivateSecondaryStore() {
        Organization organization =
                activeOrganizationWithSecondaryStore();

        repository.add(organization);

        DeactivateStoreResult result = handler.handle(
                new DeactivateStoreCommand(
                        ORGANIZATION_UUID,
                        SECONDARY_STORE_UUID
                )
        );

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.storeId())
                .isEqualTo(SECONDARY_STORE_UUID);

        assertThat(result.active()).isFalse();

        assertThat(result.deactivatedAt())
                .isEqualTo(DEACTIVATED_AT);

        Store savedStore = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow()
                .stores()
                .stream()
                .filter(store ->
                        store.id().value()
                                .equals(SECONDARY_STORE_UUID)
                )
                .findFirst()
                .orElseThrow();

        assertThat(savedStore.isActive()).isFalse();
    }

    @Test
    void shouldPublishStoreDeactivatedEvent() {
        Organization organization =
                activeOrganizationWithSecondaryStore();

        repository.add(organization);

        handler.handle(
                new DeactivateStoreCommand(
                        ORGANIZATION_UUID,
                        SECONDARY_STORE_UUID
                )
        );

        assertThat(publisher.publishedEvents())
                .singleElement()
                .isInstanceOf(StoreDeactivated.class);

        StoreDeactivated event = (StoreDeactivated)
                publisher.publishedEvents().getFirst();

        assertThat(event.eventId()).isEqualTo(EVENT_UUID);

        assertThat(event.organizationId().value())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(event.tenantId().value())
                .isEqualTo(TENANT_UUID);

        assertThat(event.storeId().value())
                .isEqualTo(SECONDARY_STORE_UUID);

        assertThat(event.occurredAt())
                .isEqualTo(DEACTIVATED_AT);
    }

    @Test
    void shouldRejectUnknownOrganization() {
        UUID unknownOrganizationId = UUID.fromString(
                "ee20db38-4666-40d2-a209-690f280ef499"
        );

        assertThatThrownBy(() -> handler.handle(
                new DeactivateStoreCommand(
                        unknownOrganizationId,
                        SECONDARY_STORE_UUID
                )
        ))
                .isInstanceOf(
                        OrganizationNotFoundException.class
                );

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void shouldRejectUnknownStore() {
        Organization organization =
                activeOrganizationWithSecondaryStore();

        repository.add(organization);

        UUID unknownStoreId = UUID.fromString(
                "ff76e82a-1665-4259-ab44-65d75fd165f0"
        );

        assertThatThrownBy(() -> handler.handle(
                new DeactivateStoreCommand(
                        ORGANIZATION_UUID,
                        unknownStoreId
                )
        ))
                .isInstanceOf(
                        BusinessRuleViolationException.class
                )
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo(
                                    "STORE_DOES_NOT_BELONG_TO_ORGANIZATION"
                            );
                });

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void shouldRejectHeadquartersDeactivation() {
        Organization organization =
                activeOrganizationWithSecondaryStore();

        repository.add(organization);

        assertThatThrownBy(() -> handler.handle(
                new DeactivateStoreCommand(
                        ORGANIZATION_UUID,
                        HEADQUARTERS_UUID
                )
        ))
                .isInstanceOf(
                        BusinessRuleViolationException.class
                )
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo(
                                    "HEADQUARTERS_CANNOT_BE_DEACTIVATED"
                            );
                });

        assertThat(publisher.publishedEvents()).isEmpty();

        Store headquarters = organization.stores()
                .stream()
                .filter(Store::isHeadquarters)
                .findFirst()
                .orElseThrow();

        assertThat(headquarters.isActive()).isTrue();
    }

    private static Organization activeOrganizationWithSecondaryStore() {
        Organization organization = Organization.register(
                new OrganizationId(ORGANIZATION_UUID),
                new TenantId(TENANT_UUID),
                new OrganizationName("Quincaillerie Thiès"),
                new LegalName("QUINCAILLERIE THIÈS SARL"),
                CurrencyCode.xof(),
                new StoreId(HEADQUARTERS_UUID),
                new StoreCode("THIES-01"),
                new StoreName("Magasin principal"),
                UUID.randomUUID(),
                CREATED_AT
        );

        organization.clearDomainEvents();

        organization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-01T11:00:00Z")
        );

        organization.clearDomainEvents();

        organization.addStore(
                new StoreId(SECONDARY_STORE_UUID),
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin Dakar"),
                UUID.randomUUID(),
                Instant.parse("2026-08-01T12:00:00Z")
        );

        organization.clearDomainEvents();

        return organization;
    }
}