package com.zim.quine.application.handler;

import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.result.AddStoreResult;

import com.zim.organization.domain.event.StoreAdded;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.CurrencyCode;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.OrganizationName;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.quine.testing.InMemoryDomainEventPublisher;
import com.zim.quine.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddStoreHandlerTest {

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

    private static final UUID NEW_STORE_UUID =
            UUID.fromString(
                    "86fd6eb4-23f6-4381-842e-e5d57def4a39"
            );

    private static final UUID STORE_ADDED_EVENT_UUID =
            UUID.fromString(
                    "1fd0bd63-ac8b-43e9-82df-918b36d4dd73"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-01T10:00:00Z");

    private static final Instant ADDED_AT =
            Instant.parse("2026-08-02T20:00:00Z");

    private InMemoryOrganizationRepository repository;
    private InMemoryDomainEventPublisher publisher;
    private AddStoreHandler handler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
        publisher = new InMemoryDomainEventPublisher();

        handler = new AddStoreHandler(
                repository,
                () -> new StoreId(NEW_STORE_UUID),
                () -> STORE_ADDED_EVENT_UUID,
                () -> ADDED_AT,
                publisher
        );
    }

    @Test
    void shouldAddStoreToActiveOrganization() {
        Organization organization = activeOrganization();
        repository.add(organization);

        AddStoreResult result = handler.handle(
                new AddStoreCommand(
                        ORGANIZATION_UUID,
                        "DAKAR-01",
                        "Magasin Dakar"
                )
        );

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.storeId())
                .isEqualTo(NEW_STORE_UUID);

        assertThat(result.storeCode())
                .isEqualTo("DAKAR-01");

        assertThat(result.storeName())
                .isEqualTo("Magasin Dakar");

        assertThat(result.headquarters()).isFalse();
        assertThat(result.active()).isTrue();
        assertThat(result.addedAt()).isEqualTo(ADDED_AT);

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.stores())
                .hasSize(2)
                .anySatisfy(store -> {
                    assertThat(store.id().value())
                            .isEqualTo(NEW_STORE_UUID);
                    assertThat(store.code().value())
                            .isEqualTo("DAKAR-01");
                });
    }

    @Test
    void shouldPublishStoreAddedEvent() {
        Organization organization = activeOrganization();
        repository.add(organization);

        handler.handle(
                new AddStoreCommand(
                        ORGANIZATION_UUID,
                        "DAKAR-01",
                        "Magasin Dakar"
                )
        );

        assertThat(publisher.publishedEvents())
                .singleElement()
                .isInstanceOf(StoreAdded.class);

        StoreAdded event =
                (StoreAdded) publisher
                        .publishedEvents()
                        .getFirst();

        assertThat(event.eventId())
                .isEqualTo(STORE_ADDED_EVENT_UUID);

        assertThat(event.organizationId().value())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(event.tenantId().value())
                .isEqualTo(TENANT_UUID);

        assertThat(event.storeId().value())
                .isEqualTo(NEW_STORE_UUID);

        assertThat(event.storeCode().value())
                .isEqualTo("DAKAR-01");

        assertThat(event.occurredAt())
                .isEqualTo(ADDED_AT);
    }

    @Test
    void shouldRejectDuplicateStoreCode() {
        Organization organization = activeOrganization();

        assertThatThrownBy(() -> organization.addStore(
                new StoreId(UUID.randomUUID()),
                new StoreCode("THIES-01"),
                new StoreName("Autre magasin"),
                UUID.randomUUID(),
                Instant.parse("2026-08-01T12:00:00Z")
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo("STORE_CODE_ALREADY_EXISTS");

                    assertThat(exception.getMessage())
                            .contains("already exists");
                });

        assertThat(organization.stores()).hasSize(1);
        assertThat(organization.domainEvents()).isEmpty();
    }

    @Test
    void shouldRejectStoreAdditionWhenOrganizationIsPending() {
        Organization organization = pendingOrganization();
        organization.clearDomainEvents();
        repository.add(organization);

        assertThatThrownBy(() -> handler.handle(
                new AddStoreCommand(
                        ORGANIZATION_UUID,
                        "DAKAR-01",
                        "Magasin Dakar"
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
                                    "ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE"
                            );
                });

        assertThat(publisher.publishedEvents()).isEmpty();
        assertThat(organization.stores()).hasSize(1);
    }

    @Test
    void shouldRejectUnknownOrganization() {
        UUID unknownId = UUID.fromString(
                "a603d0df-3e99-47a8-9e0d-666ed65ad07a"
        );

        assertThatThrownBy(() -> handler.handle(
                new AddStoreCommand(
                        unknownId,
                        "DAKAR-01",
                        "Magasin Dakar"
                )
        ))
                .isInstanceOf(
                        OrganizationNotFoundException.class
                );

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    private static Organization activeOrganization() {
        Organization organization = pendingOrganization();

        organization.clearDomainEvents();

        organization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-01T12:00:00Z")
        );

        organization.clearDomainEvents();

        assertThat(organization.status())
                .isEqualTo(OrganizationStatus.ACTIVE);

        return organization;
    }

    private static Organization pendingOrganization() {
        return Organization.register(
                new OrganizationId(ORGANIZATION_UUID),
                new TenantId(TENANT_UUID),
                new OrganizationName("Quincaillerie Thiès"),
                "QUINCAILLERIE THIÈS SARL",
                CurrencyCode.xof(),
                new StoreId(HEADQUARTERS_UUID),
                new StoreCode("THIES-01"),
                new StoreName("Magasin principal"),
                UUID.randomUUID(),
                CREATED_AT
        );
    }
}