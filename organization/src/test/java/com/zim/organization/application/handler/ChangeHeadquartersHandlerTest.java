package com.zim.organization.application.handler;

import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.exception.OrganizationNotFoundException;
import com.zim.organization.application.result.ChangeHeadquartersResult;
import com.zim.organization.domain.event.HeadquartersChanged;
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

class ChangeHeadquartersHandlerTest {

  private static final UUID ORGANIZATION_UUID =
      UUID.fromString(
          "0f4b8a2e-6c1d-4b5e-9a3f-2d7c8e1b4a60"
      );

  private static final UUID TENANT_UUID =
      UUID.fromString(
          "7e2c9d14-3b8a-4f6e-a1c5-9d0b2e4f6a81"
      );

  private static final UUID HEADQUARTERS_UUID =
      UUID.fromString(
          "a3d5f7b9-1c2e-4a6b-8d0f-3e5a7c9b1d24"
      );

  private static final UUID SECONDARY_STORE_UUID =
      UUID.fromString(
          "c8e1a4b7-5d2f-4c9a-b3e6-7f0a2d5c8e13"
      );

  private static final UUID EVENT_UUID =
      UUID.fromString(
          "e6b2d9f4-8a1c-4e7b-9c3d-5a0f2b8e6d47"
      );

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-01T10:00:00Z");

  private static final Instant CHANGED_AT =
      Instant.parse("2026-08-02T12:00:00Z");

  private InMemoryOrganizationRepository repository;
  private InMemoryDomainEventPublisher publisher;
  private ChangeHeadquartersHandler handler;

  @BeforeEach
  void setUp() {
    repository = new InMemoryOrganizationRepository();
    publisher = new InMemoryDomainEventPublisher();

    handler = new ChangeHeadquartersHandler(
        repository,
        () -> EVENT_UUID,
        () -> CHANGED_AT,
        publisher
    );
  }

  @Test
  void shouldChangeHeadquarters() {
    repository.add(activeOrganizationWithSecondaryStore());

    ChangeHeadquartersResult result = handler.handle(
        new ChangeHeadquartersCommand(
            ORGANIZATION_UUID,
            SECONDARY_STORE_UUID
        )
    );

    assertThat(result.organizationId())
        .isEqualTo(ORGANIZATION_UUID);

    assertThat(result.headquartersId())
        .isEqualTo(SECONDARY_STORE_UUID);

    assertThat(result.changedAt())
        .isEqualTo(CHANGED_AT);

    Organization savedOrganization = repository
        .findById(new OrganizationId(ORGANIZATION_UUID))
        .orElseThrow();

    assertThat(savedOrganization.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store ->
            assertThat(store.id().value())
                .isEqualTo(SECONDARY_STORE_UUID)
        );

    assertThat(savedOrganization.domainEvents()).isEmpty();
  }

  @Test
  void shouldPublishHeadquartersChangedEvent() {
    repository.add(activeOrganizationWithSecondaryStore());

    handler.handle(
        new ChangeHeadquartersCommand(
            ORGANIZATION_UUID,
            SECONDARY_STORE_UUID
        )
    );

    assertThat(publisher.publishedEvents())
        .singleElement()
        .isInstanceOf(HeadquartersChanged.class);

    HeadquartersChanged event = (HeadquartersChanged)
        publisher.publishedEvents().getFirst();

    assertThat(event.eventId()).isEqualTo(EVENT_UUID);

    assertThat(event.organizationId().value())
        .isEqualTo(ORGANIZATION_UUID);

    assertThat(event.tenantId().value())
        .isEqualTo(TENANT_UUID);

    assertThat(event.previousHeadquartersId().value())
        .isEqualTo(HEADQUARTERS_UUID);

    assertThat(event.newHeadquartersId().value())
        .isEqualTo(SECONDARY_STORE_UUID);

    assertThat(event.occurredAt())
        .isEqualTo(CHANGED_AT);
  }

  @Test
  void shouldRejectUnknownOrganization() {
    UUID unknownOrganizationId = UUID.fromString(
        "1b3d5f7a-9c2e-4d6f-8a0b-2c4e6a8d0f13"
    );

    assertThatThrownBy(() -> handler.handle(
        new ChangeHeadquartersCommand(
            unknownOrganizationId,
            SECONDARY_STORE_UUID
        )
    ))
        .isInstanceOf(OrganizationNotFoundException.class);

    assertThat(publisher.publishedEvents()).isEmpty();
  }

  @Test
  void shouldRejectUnknownStore() {
    repository.add(activeOrganizationWithSecondaryStore());

    UUID unknownStoreId = UUID.fromString(
        "5e7a9c1b-3d5f-4e8a-a2c4-6f8b0d2e4a79"
    );

    assertThatThrownBy(() -> handler.handle(
        new ChangeHeadquartersCommand(
            ORGANIZATION_UUID,
            unknownStoreId
        )
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo(
                  "STORE_DOES_NOT_BELONG_TO_ORGANIZATION"
              );
        });

    assertHeadquartersUnchanged();
    assertThat(publisher.publishedEvents()).isEmpty();
  }

  @Test
  void shouldRejectInactiveTargetStore() {
    Organization organization =
        activeOrganizationWithSecondaryStore();

    organization.deactivateStore(
        new StoreId(SECONDARY_STORE_UUID),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T11:00:00Z")
    );

    organization.clearDomainEvents();

    repository.add(organization);

    assertThatThrownBy(() -> handler.handle(
        new ChangeHeadquartersCommand(
            ORGANIZATION_UUID,
            SECONDARY_STORE_UUID
        )
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo(
                  "INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS"
              );
        });

    assertHeadquartersUnchanged();
    assertThat(publisher.publishedEvents()).isEmpty();
  }

  @Test
  void shouldRejectStoreThatIsAlreadyHeadquarters() {
    repository.add(activeOrganizationWithSecondaryStore());

    assertThatThrownBy(() -> handler.handle(
        new ChangeHeadquartersCommand(
            ORGANIZATION_UUID,
            HEADQUARTERS_UUID
        )
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("STORE_IS_ALREADY_HEADQUARTERS");
        });

    assertHeadquartersUnchanged();
    assertThat(publisher.publishedEvents()).isEmpty();
  }

  private void assertHeadquartersUnchanged() {
    assertThat(
        repository
            .findById(new OrganizationId(ORGANIZATION_UUID))
            .orElseThrow()
            .stores()
    )
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store ->
            assertThat(store.id().value())
                .isEqualTo(HEADQUARTERS_UUID)
        );
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
