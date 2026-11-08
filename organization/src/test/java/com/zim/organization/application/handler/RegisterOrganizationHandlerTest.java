package com.zim.organization.application.handler;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.event.OrganizationRegistered;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.TenantId;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  private static final String OWNER_EMAIL = "owner@example.com";
  // Fictitious test value of the required length.
  private static final String OWNER_PASSWORD = "not-a-real-password-1";

  private InMemoryDomainEventPublisher publisher;
  private List<String> calls;
  private List<Object[]> ownerCalls;
  private RuntimeException ownerFailure;

  @BeforeEach
  void setUp() {
    calls = new ArrayList<>();
    ownerCalls = new ArrayList<>();
    ownerFailure = null;
    repository = new InMemoryOrganizationRepository();
    publisher = new InMemoryDomainEventPublisher();

    handler = new RegisterOrganizationHandler(
        new OrganizationRepository() {
          @Override
          public void save(Organization organization) {
            calls.add("save");
            repository.save(organization);
          }

          @Override
          public Optional<Organization> findById(
              TenantId tenantId, OrganizationId organizationId
          ) {
            return repository.findById(tenantId, organizationId);
          }

          @Override
          public boolean existsByLegalName(String normalizedLegalName) {
            return repository.existsByLegalName(normalizedLegalName);
          }
        },
        () -> new OrganizationId(ORGANIZATION_UUID),
        () -> new TenantId(TENANT_UUID),
        () -> new StoreId(STORE_UUID),
        () -> EVENT_UUID,
        () -> FIXED_TIME,
        events -> {
          calls.add("publish");
          publisher.publish(events);
        },
        (tenantId, email, password) -> {
          calls.add("owner");
          ownerCalls.add(new Object[] {tenantId, email, password});
          if (ownerFailure != null) {
            throw ownerFailure;
          }
        }
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
            "Magasin principal",
            OWNER_EMAIL,
            OWNER_PASSWORD
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
        new TenantId(TENANT_UUID),
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
            "Magasin principal",
            OWNER_EMAIL,
            OWNER_PASSWORD
        );

    handler.handle(firstCommand);

    RegisterOrganizationCommand duplicateCommand =
        new RegisterOrganizationCommand(
            "Autre nom commercial",
            "  quincaillerie   thiès sarl ",
            "XOF",
            "DAKAR-01",
            "Magasin Dakar",
            OWNER_EMAIL,
            OWNER_PASSWORD
        );

    assertThatThrownBy(
        () -> handler.handle(duplicateCommand)
    )
        .isInstanceOf(
            OrganizationAlreadyExistsException.class
        )
        .hasMessageContaining(
            "quincaillerie thiès sarl"
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
            "Magasin principal",
            OWNER_EMAIL,
            OWNER_PASSWORD
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

  @Test
  void shouldRegisterOwnerBetweenSaveAndEvent() {
    handler.handle(validCommand());

    assertThat(calls).containsExactly("save", "owner", "publish");
  }

  @Test
  void shouldPassTenantEmailAndPasswordToTheOwnerPort() {
    handler.handle(validCommand());

    assertThat(ownerCalls).singleElement().satisfies(call -> {
      assertThat(call[0]).isEqualTo(new TenantId(TENANT_UUID));
      assertThat(call[1]).isEqualTo(OWNER_EMAIL);
      assertThat(call[2]).isEqualTo(OWNER_PASSWORD);
    });
  }

  /**
   * The exception reaches the caller unchanged (the transaction wrapper then
   * rolls back the save) and no event announces a registration that failed.
   */
  @Test
  void shouldPropagateOwnerFailureAndPublishNothing() {
    ownerFailure = new IllegalStateException("owner refused");

    assertThatThrownBy(() -> handler.handle(validCommand()))
        .isSameAs(ownerFailure);

    assertThat(publisher.publishedEvents()).isEmpty();
    assertThat(calls).containsExactly("save", "owner");
  }

  @Test
  void shouldNotCallOwnerPortWhenLegalNameAlreadyExists() {
    handler.handle(validCommand());
    ownerCalls.clear();

    assertThatThrownBy(() -> handler.handle(validCommand()))
        .isInstanceOf(OrganizationAlreadyExistsException.class);

    assertThat(ownerCalls).isEmpty();
  }

  @Test
  void shouldKeepOwnerDataOutOfTheRegisteredEvent() {
    handler.handle(validCommand());

    assertThat(publisher.publishedEvents().toString())
        .doesNotContain(OWNER_EMAIL)
        .doesNotContain(OWNER_PASSWORD);
  }

  private static RegisterOrganizationCommand validCommand() {
    return new RegisterOrganizationCommand(
        "Quincaillerie Thiès",
        "Quincaillerie Thiès SARL",
        "XOF",
        "THIES-01",
        "Magasin principal",
        OWNER_EMAIL,
        OWNER_PASSWORD
    );
  }
}