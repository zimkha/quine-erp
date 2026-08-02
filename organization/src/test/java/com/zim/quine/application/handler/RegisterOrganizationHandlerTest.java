package com.zim.quine.application.handler;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.event.OrganizationRegistered;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.quine.testing.InMemoryDomainEventPublisher;
import com.zim.quine.testing.InMemoryOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterOrganizationHandlerTest {

    private static final UUID ORGANIZATION_UUID =
            UUID.fromString("5c80d578-83f7-4b44-b5f7-598530067a09");

    private static final UUID TENANT_UUID =
            UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");

    private static final UUID STORE_UUID =
            UUID.fromString("4ee0d038-4617-435c-b7c8-48697d4cf909");

    private static final Instant FIXED_TIME =
            Instant.parse("2026-08-01T20:00:00Z");

    private InMemoryOrganizationRepository repository;
    private RegisterOrganizationHandler handler;
    private static final UUID EVENT_UUID =
            UUID.fromString(
                    "f84f8ce3-803f-45a5-a552-d7313f9f7cf8"
            );

    private InMemoryDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository();
        publisher = new InMemoryDomainEventPublisher();

        handler = new RegisterOrganizationHandler(
                repository,
                () -> new OrganizationId(ORGANIZATION_UUID),
                () -> new TenantId(TENANT_UUID),
                () -> new StoreId(STORE_UUID),
                () -> EVENT_UUID,
                () -> FIXED_TIME,
                publisher
        );
    }

    @Test
    void shouldRegisterOrganization() {
        RegisterOrganizationCommand command =
                new RegisterOrganizationCommand(
                        "Quincaillerie Thiès",
                        "Quincaillerie Thiès SARL",
                        "XOF",
                        "THIES-01",
                        "Magasin principal"
                );

        RegisterOrganizationResult result =
                handler.handle(command);

        assertThat(result.organizationId())
                .isEqualTo(ORGANIZATION_UUID);

        assertThat(result.tenantId())
                .isEqualTo(TENANT_UUID);

        assertThat(result.headquartersId())
                .isEqualTo(STORE_UUID);

        assertThat(result.organizationName())
                .isEqualTo("Quincaillerie Thiès");

        assertThat(result.status())
                .isEqualTo(
                        OrganizationStatus.PENDING_ACTIVATION.name()
                );

        assertThat(result.createdAt())
                .isEqualTo(FIXED_TIME);

        assertThat(repository.findById(
                new OrganizationId(ORGANIZATION_UUID)
        )).isPresent();
    }

    @Test
    void shouldRejectDuplicateLegalName() {
        RegisterOrganizationCommand firstCommand =
                new RegisterOrganizationCommand(
                        "Quincaillerie Thiès",
                        "Quincaillerie Thiès SARL",
                        "XOF",
                        "THIES-01",
                        "Magasin principal"
                );

        handler.handle(firstCommand);

        RegisterOrganizationCommand duplicateCommand =
                new RegisterOrganizationCommand(
                        "Autre nom commercial",
                        "  quincaillerie   thiès sarl ",
                        "XOF",
                        "DAKAR-01",
                        "Magasin Dakar"
                );

        assertThatThrownBy(
                () -> handler.handle(duplicateCommand)
        )
                .isInstanceOf(
                        OrganizationAlreadyExistsException.class
                )
                .hasMessageContaining(
                        "QUINCAILLERIE THIÈS SARL"
                );
    }

    @Test
    void shouldPublishOrganizationRegisteredEvent() {
        RegisterOrganizationCommand command =
                new RegisterOrganizationCommand(
                        "Quincaillerie Thiès",
                        "Quincaillerie Thiès SARL",
                        "XOF",
                        "THIES-01",
                        "Magasin principal"
                );

        handler.handle(command);

        assertThat(publisher.publishedEvents())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event)
                            .isInstanceOf(
                                    OrganizationRegistered.class
                            );

                    OrganizationRegistered registered =
                            (OrganizationRegistered) event;

                    assertThat(registered.eventId())
                            .isEqualTo(EVENT_UUID);

                    assertThat(registered.organizationId().value())
                            .isEqualTo(ORGANIZATION_UUID);
                });
    }
}