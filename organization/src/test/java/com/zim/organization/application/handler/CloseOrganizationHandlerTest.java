package com.zim.organization.application.handler;

import com.zim.organization.application.command.CloseOrganizationCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.result.CloseOrganizationResult;

import com.zim.organization.domain.event.OrganizationClosed;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloseOrganizationHandlerTest {

    private static final UUID ORGANIZATION_UUID =
            UUID.fromString("5c80d578-83f7-4b44-b5f7-598530067a09");

    private static final UUID TENANT_UUID =
            UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");

    private static final UUID HEADQUARTERS_UUID =
            UUID.fromString("4ee0d038-4617-435c-b7c8-48697d4cf909");

    private static final UUID EVENT_UUID =
            UUID.fromString("0b4410bd-25f2-4ab5-88c9-1d83a989eb84");

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-01T10:00:00Z");

    private static final Instant ACTIVATED_AT =
            Instant.parse("2026-08-02T10:00:00Z");

    private static final Instant SUSPENDED_AT =
            Instant.parse("2026-08-02T11:00:00Z");

    private static final Instant CLOSED_AT =
            Instant.parse("2026-08-03T10:00:00Z");

    private InMemoryOrganizationRepository repository;
    private InMemoryDomainEventPublisher publisher;
    private CloseOrganizationHandler handler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
        publisher = new InMemoryDomainEventPublisher();

        handler = new CloseOrganizationHandler(
                repository,
                () -> EVENT_UUID,
                () -> CLOSED_AT,
                publisher
        );
    }

    @Test
    void shouldCloseActiveOrganization() {
        Organization organization = activeOrganization();

        repository.add(organization);

        CloseOrganizationResult result = handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        );

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.tenantId())
                .isEqualTo(TENANT_UUID);

        assertThat(result.status())
                .isEqualTo(OrganizationStatus.CLOSED.name());

        assertThat(result.closedAt())
                .isEqualTo(CLOSED_AT);

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.status())
                .isEqualTo(OrganizationStatus.CLOSED);

        assertThat(savedOrganization.domainEvents())
                .isEmpty();
    }

    @Test
    void shouldCloseSuspendedOrganization() {
        Organization organization = suspendedOrganization();

        repository.add(organization);

        CloseOrganizationResult result = handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        );

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.status())
                .isEqualTo(OrganizationStatus.CLOSED.name());

        assertThat(result.closedAt())
                .isEqualTo(CLOSED_AT);

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.status())
                .isEqualTo(OrganizationStatus.CLOSED);
    }

    @Test
    void shouldPublishOrganizationClosedEvent() {
        repository.add(activeOrganization());

        handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        );

        assertThat(publisher.publishedEvents())
                .singleElement()
                .isInstanceOf(OrganizationClosed.class);

        OrganizationClosed event =
                (OrganizationClosed) publisher
                        .publishedEvents()
                        .getFirst();

        assertThat(event.eventId())
                .isEqualTo(EVENT_UUID);

        assertThat(event.organizationId().value())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(event.tenantId().value())
                .isEqualTo(TENANT_UUID);

        assertThat(event.occurredAt())
                .isEqualTo(CLOSED_AT);

        assertThat(event.eventType())
                .isEqualTo("organization.closed.v1");
    }

    @Test
    void shouldRejectUnknownOrganization() {
        UUID unknownOrganizationId =
                UUID.fromString("ee20db38-4666-40d2-a209-690f280ef499");

        assertThatThrownBy(() -> handler.handle(
                new CloseOrganizationCommand(unknownOrganizationId)
        ))
                .isInstanceOf(OrganizationNotFoundException.class)
                .satisfies(throwable -> {
                    OrganizationNotFoundException exception =
                            (OrganizationNotFoundException) throwable;

                    assertThat(exception.code())
                            .isEqualTo("ORGANIZATION_NOT_FOUND");

                    assertThat(exception.organizationId())
                            .isEqualTo(unknownOrganizationId);
                });

        assertThat(publisher.publishedEvents())
                .isEmpty();
    }

    @Test
    void shouldRejectClosingPendingOrganization() {
        Organization organization = pendingOrganization();

        organization.clearDomainEvents();
        repository.add(organization);

        assertThatThrownBy(() -> handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo("ORGANIZATION_CANNOT_BE_CLOSED");

                    assertThat(exception.ruleName())
                            .isEqualTo("OrganizationMustBeClosableRule");
                });

        assertThat(organization.status())
                .isEqualTo(OrganizationStatus.PENDING_ACTIVATION);

        assertThat(organization.domainEvents())
                .isEmpty();

        assertThat(publisher.publishedEvents())
                .isEmpty();
    }

    @Test
    void shouldRejectClosingAlreadyClosedOrganization() {
        Organization organization = activeOrganization();

        organization.close(
                UUID.randomUUID(),
                Instant.parse("2026-08-03T09:00:00Z")
        );

        organization.clearDomainEvents();
        repository.add(organization);

        assertThatThrownBy(() -> handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(throwable -> {
                    BusinessRuleViolationException exception =
                            (BusinessRuleViolationException) throwable;

                    assertThat(exception.code())
                            .isEqualTo("ORGANIZATION_ALREADY_CLOSED");

                    assertThat(exception.ruleName())
                            .isEqualTo("OrganizationMustBeClosableRule");
                });

        assertThat(organization.status())
                .isEqualTo(OrganizationStatus.CLOSED);

        assertThat(organization.domainEvents())
                .isEmpty();

        assertThat(publisher.publishedEvents())
                .isEmpty();
    }

    @Test
    void shouldRemovePublishedEventsFromAggregate() {
        Organization organization = activeOrganization();

        repository.add(organization);

        handler.handle(
                new CloseOrganizationCommand(ORGANIZATION_UUID)
        );

        Organization savedOrganization = repository
                .findById(new OrganizationId(ORGANIZATION_UUID))
                .orElseThrow();

        assertThat(savedOrganization.domainEvents())
                .isEmpty();

        assertThat(publisher.publishedEvents())
                .hasSize(1);
    }

    private static Organization suspendedOrganization() {
        Organization organization = activeOrganization();

        organization.suspend(UUID.randomUUID(), SUSPENDED_AT);

        organization.clearDomainEvents();

        return organization;
    }

    private static Organization activeOrganization() {
        Organization organization = pendingOrganization();

        organization.clearDomainEvents();

        organization.activate(
                UUID.randomUUID(),
                ACTIVATED_AT
        );

        organization.clearDomainEvents();

        return organization;
    }

    private static Organization pendingOrganization() {
        return Organization.register(
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
    }
}