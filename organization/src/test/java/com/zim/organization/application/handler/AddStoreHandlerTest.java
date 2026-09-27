package com.zim.organization.application.handler;

import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.result.AddStoreResult;

import com.zim.organization.domain.event.StoreAdded;
import com.zim.organization.domain.exception.InvalidValueException;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.TenantId;
import com.zim.organization.testing.InMemoryDomainEventPublisher;
import com.zim.organization.testing.InMemoryOrganizationRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.zim.organization.testing.OrganizationAssertions.assertOrganizationNotFound;
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

  private static final UUID OTHER_TENANT_UUID =
      UUID.fromString("9a4c2e71-5b3d-4f8a-b6c1-0d2e4f6a8b13");

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
  LegalName legalName;
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
            new TenantId(TENANT_UUID),
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
        .findById(
            new TenantId(TENANT_UUID),
            new OrganizationId(ORGANIZATION_UUID)
        )
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
            new TenantId(TENANT_UUID),
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
  void shouldRejectDuplicateStoreCodeIgnoringCase() {
    Organization organization = activeOrganization();
    repository.add(organization);

    assertThatThrownBy(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(TENANT_UUID),
            ORGANIZATION_UUID,
            "thies-01",
            "Magasin 2"
        )
    ))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("STORE_CODE_ALREADY_EXISTS")
        );

    assertThat(repository.saveCount()).isZero();
    assertThat(publisher.publishedEvents()).isEmpty();
    assertThat(organization.stores()).hasSize(1);
  }

  @Test
  void shouldRejectCodeOfInactiveStore() {
    Organization organization = activeOrganization();
    StoreId dakarId = new StoreId(UUID.randomUUID());
    organization.addStore(
        dakarId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-01T13:00:00Z")
    );
    organization.deactivateStore(
        dakarId,
        UUID.randomUUID(),
        Instant.parse("2026-08-01T14:00:00Z")
    );
    organization.clearDomainEvents();
    repository.add(organization);

    assertThatThrownBy(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(TENANT_UUID),
            ORGANIZATION_UUID,
            "DAKAR-01",
            "Magasin Dakar bis"
        )
    ))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("STORE_CODE_ALREADY_EXISTS")
        );

    assertThat(repository.saveCount()).isZero();
    assertThat(publisher.publishedEvents()).isEmpty();
    assertThat(organization.stores()).hasSize(2);
  }

  @Test
  void shouldRejectInvalidStoreNameBeforeCheckingOrganizationStatus() {
    // Value objects are built before the rules run: a name that is too
    // short once trimmed wins over the "must be active" rule.
    Organization organization = pendingOrganization();
    organization.clearDomainEvents();
    repository.add(organization);

    assertThatThrownBy(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(TENANT_UUID),
            ORGANIZATION_UUID,
            "DAKAR-01",
            " a"
        )
    ))
        .isInstanceOfSatisfying(
            InvalidValueException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("INVALID_STORE_NAME")
        );

    assertThat(repository.saveCount()).isZero();
    assertThat(publisher.publishedEvents()).isEmpty();
    assertThat(organization.stores()).hasSize(1);
  }

  @Test
  void shouldRejectStoreAdditionWhenOrganizationIsPending() {
    Organization organization = pendingOrganization();
    organization.clearDomainEvents();
    repository.add(organization);

    assertThatThrownBy(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(TENANT_UUID),
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
    assertOrganizationNotFound(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(TENANT_UUID),
            ORGANIZATION_UUID,
            "DAKAR-01",
            "Magasin Dakar"
        )
    ), ORGANIZATION_UUID, repository, publisher);
  }

  @Test
  void shouldNotAddStoreToOrganizationOwnedByAnotherTenant() {
    Organization organization = activeOrganization();
    repository.add(organization);

    assertOrganizationNotFound(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(OTHER_TENANT_UUID),
            ORGANIZATION_UUID,
            "DAKAR-01",
            "Magasin Dakar"
        )
    ), ORGANIZATION_UUID, repository, publisher);

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.ACTIVE);
    assertThat(organization.stores())
        .singleElement()
        .satisfies(store -> assertThat(store.id().value())
            .isEqualTo(HEADQUARTERS_UUID));
    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldReportNotFoundRatherThanRuleViolationForAnotherTenant() {
    // Adding a store to a CLOSED organization breaks a rule for its owner,
    // but another tenant must not learn the organization's state.
    Organization organization = activeOrganization();
    organization.close(
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    );
    organization.clearDomainEvents();
    repository.add(organization);

    assertOrganizationNotFound(() -> handler.handle(
        new AddStoreCommand(
            new TenantId(OTHER_TENANT_UUID),
            ORGANIZATION_UUID,
            "DAKAR-01",
            "Magasin Dakar"
        )
    ), ORGANIZATION_UUID, repository, publisher);

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.CLOSED);
    assertThat(organization.stores()).hasSize(1);
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