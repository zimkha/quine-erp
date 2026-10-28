package com.zim.organization.application.handler;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.OwnerRegistrar;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.event.OrganizationRegistered;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.TenantId;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
  private final List<RegisteredOwner> registeredOwners = new ArrayList<>();
  private OwnerRegistrar ownerRegistrar;

  private record RegisteredOwner(
      TenantId tenantId, String email, String password) {
  }

  @BeforeEach
  void setUp() {
    repository = new InMemoryOrganizationRepository();
    publisher = new InMemoryDomainEventPublisher();
    registeredOwners.clear();
    ownerRegistrar = (tenantId, email, password) ->
        registeredOwners.add(new RegisteredOwner(tenantId, email, password));

    handler = new RegisterOrganizationHandler(
        repository,
        () -> new OrganizationId(ORGANIZATION_UUID),
        () -> new TenantId(TENANT_UUID),
        () -> new StoreId(STORE_UUID),
        () -> EVENT_UUID,
        () -> FIXED_TIME,
        publisher,
        ownerRegistrar
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
            "owner-" + UUID.randomUUID() + "@example.com",
            "placeholder-owner-value"
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
            "owner-" + UUID.randomUUID() + "@example.com",
            "placeholder-owner-value"
        );

    handler.handle(firstCommand);

    RegisterOrganizationCommand duplicateCommand =
        new RegisterOrganizationCommand(
            "Autre nom commercial",
            "  quincaillerie   thiès sarl ",
            "XOF",
            "DAKAR-01",
            "Magasin Dakar",
            "owner-" + UUID.randomUUID() + "@example.com",
            "placeholder-owner-value"
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
            "owner-" + UUID.randomUUID() + "@example.com",
            "placeholder-owner-value"
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
  void shouldRegisterTheOwnerForTheNewTenant() {
    handler.handle(command());

    assertThat(registeredOwners).containsExactly(new RegisteredOwner(
        new TenantId(TENANT_UUID),
        "owner@example.com",
        "placeholder-owner-value"
    ));
  }

  @Test
  void shouldRegisterTheOwnerBeforePublishingTheEvent() {
    List<String> order = new ArrayList<>();
    ownerRegistrar = (tenantId, email, password) -> order.add("owner");
    handler = handlerWith(ownerRegistrar, events -> order.add("event"));

    handler.handle(command());

    assertThat(order).containsExactly("owner", "event");
  }

  @Test
  void shouldPropagateOwnerFailureAndPublishNothing() {
    ownerRegistrar = (tenantId, email, password) -> {
      throw new IllegalStateException("owner creation failed");
    };
    handler = handlerWith(ownerRegistrar, publisher);

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(IllegalStateException.class);

    // The save is undone by the handler's transaction (proved against
    // Postgres by RegistrationAtomicityIT); here the exception must reach
    // the caller and no event may go out.
    assertThat(publisher.publishedEvents()).isEmpty();
  }

  @Test
  void shouldNotCreateAnOwnerWhenTheLegalNameIsTaken() {
    handler.handle(command());
    registeredOwners.clear();

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(OrganizationAlreadyExistsException.class);

    assertThat(registeredOwners).isEmpty();
  }

  @Test
  void shouldNotShowThePasswordInTheCommandText() {
    assertThat(command().toString())
        .doesNotContain("placeholder-owner-value")
        .contains("[PROTECTED]");
  }

  private static RegisterOrganizationCommand command() {
    return new RegisterOrganizationCommand(
        "Quincaillerie Thiès",
        "Quincaillerie Thiès SARL",
        "XOF",
        "THIES-01",
        "Magasin principal",
        "owner@example.com",
        "placeholder-owner-value"
    );
  }

  private RegisterOrganizationHandler handlerWith(
      OwnerRegistrar registrar,
      DomainEventPublisher eventPublisher
  ) {
    return new RegisterOrganizationHandler(
        repository,
        () -> new OrganizationId(ORGANIZATION_UUID),
        () -> new TenantId(TENANT_UUID),
        () -> new StoreId(STORE_UUID),
        () -> EVENT_UUID,
        () -> FIXED_TIME,
        eventPublisher,
        registrar
    );
  }
}
