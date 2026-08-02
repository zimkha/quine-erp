package com.zim.quine.application.handler;

import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.domain.event.OrganizationActivated;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.quine.testing.InMemoryDomainEventPublisher;
import com.zim.quine.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivateOrganizationHandlerTest {

    private static final UUID ORGANIZATION_UUID =
            UUID.fromString(
                    "5c80d578-83f7-4b44-b5f7-598530067a09"
            );

    private static final UUID TENANT_UUID =
            UUID.fromString(
                    "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
            );

    private static final UUID STORE_UUID =
            UUID.fromString(
                    "4ee0d038-4617-435c-b7c8-48697d4cf909"
            );

    private static final UUID ACTIVATION_EVENT_UUID =
            UUID.fromString(
                    "5a428239-6f21-48fb-8d6f-a7953cbb3a97"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-01T10:00:00Z");

    private static final Instant ACTIVATED_AT =
            Instant.parse("2026-08-02T10:00:00Z");

    private InMemoryOrganizationRepository repository;
    private InMemoryDomainEventPublisher publisher;
    private ActivateOrganizationHandler handler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
        publisher = new InMemoryDomainEventPublisher();

        handler = new ActivateOrganizationHandler(
                repository,
                () -> ACTIVATION_EVENT_UUID,
                () -> ACTIVATED_AT,
                publisher
        );
    }

    @Test
    void shouldActivatePendingOrganization() {
        Organization organization = pendingOrganization();

        // L’événement d’enregistrement appartient au cas d’usage précédent.
        organization.clearDomainEvents();

        repository.add(organization);

        ActivateOrganizationResult result = handler.handle(
                new ActivateOrganizationCommand(ORGANIZATION_UUID)
        );

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.tenantId())
                .isEqualTo(TENANT_UUID);

        assertThat(result.status())
                .isEqualTo(OrganizationStatus.ACTIVE.name());

        assertThat(result.activatedAt())
                .isEqualTo(ACTIVATED_AT);

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.status())
                .isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void shouldPublishOrganizationActivatedEvent() {
        Organization organization = pendingOrganization();
        organization.clearDomainEvents();

        repository.add(organization);

        handler.handle(
                new ActivateOrganizationCommand(ORGANIZATION_UUID)
        );

        assertThat(publisher.publishedEvents())
                .singleElement()
                .isInstanceOf(OrganizationActivated.class);

        OrganizationActivated event =
                (OrganizationActivated)
                        publisher.publishedEvents().getFirst();

        assertThat(event.eventId())
                .isEqualTo(ACTIVATION_EVENT_UUID);

        assertThat(event.organizationId().value())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(event.tenantId().value())
                .isEqualTo(TENANT_UUID);

        assertThat(event.occurredAt())
                .isEqualTo(ACTIVATED_AT);
    }

    @Test
    void shouldRemovePublishedEventsFromAggregate() {
        Organization organization = pendingOrganization();
        organization.clearDomainEvents();

        repository.add(organization);

        handler.handle(
                new ActivateOrganizationCommand(ORGANIZATION_UUID)
        );

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.domainEvents())
                .isEmpty();
    }

    @Test
    void shouldRejectUnknownOrganization() {
        UUID unknownOrganizationId =
                UUID.fromString(
                        "dc1ad060-e435-4f82-ab8a-d6755258b7c2"
                );

        assertThatThrownBy(
                () -> handler.handle(
                        new ActivateOrganizationCommand(
                                unknownOrganizationId
                        )
                )
        )
                .isInstanceOf(
                        OrganizationNotFoundException.class
                )
                .satisfies(throwable -> {
                    OrganizationNotFoundException exception =
                            (OrganizationNotFoundException) throwable;

                    assertThat(exception.code())
                            .isEqualTo("ORGANIZATION_NOT_FOUND");

                    assertThat(exception.organizationId())
                            .isEqualTo(unknownOrganizationId);
                });

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void shouldRejectAlreadyActiveOrganization() {
        Organization organization = pendingOrganization();
        organization.clearDomainEvents();

        organization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-01T12:00:00Z")
        );

        organization.clearDomainEvents();
        repository.add(organization);

        assertThatThrownBy(
                () -> handler.handle(
                        new ActivateOrganizationCommand(
                                ORGANIZATION_UUID
                        )
                )
        )
                .isInstanceOf(
                        BusinessRuleViolationException.class
                )
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo(
                                    "ORGANIZATION_CANNOT_BE_ACTIVATED"
                            );

                    assertThat(exception.ruleName())
                            .isEqualTo(
                                    "OrganizationMustBeActivatableRule"
                            );
                });

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    private static Organization pendingOrganization() {
        return Organization.register(
                new OrganizationId(ORGANIZATION_UUID),
                new TenantId(TENANT_UUID),
                new OrganizationName("Quincaillerie Thiès"),
                "QUINCAILLERIE THIÈS SARL",
                CurrencyCode.xof(),
                new StoreId(STORE_UUID),
                new StoreCode("THIES-01"),
                new StoreName("Magasin principal"),
                UUID.fromString(
                        "f84f8ce3-803f-45a5-a552-d7313f9f7cf8"
                ),
                CREATED_AT
        );
    }
}