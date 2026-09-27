package com.zim.organization.domain.model;

import com.zim.organization.domain.event.HeadquartersChanged;
import com.zim.organization.domain.event.OrganizationClosed;
import com.zim.organization.domain.event.OrganizationRegistered;
import com.zim.organization.domain.event.OrganizationSuspended;
import com.zim.organization.domain.event.StoreDeactivated;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.TenantId;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationTest {

  @Test
  void shouldRegisterOrganizationWithOneHeadquarters() {
    Organization organization = newOrganization();

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.PENDING_ACTIVATION);

    assertThat(organization.stores())
        .hasSize(1);

    assertThat(organization.stores().getFirst().isHeadquarters())
        .isTrue();
  }

  @Test
  void shouldActivatePendingOrganization() {
    Organization organization = newOrganization();

    organization.activate(
        UUID.randomUUID(),
        Instant.parse("2026-08-01T12:00:00Z")
    );

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.ACTIVE);
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
  void shouldAddStore() {
    Organization organization = activeOrganization();

    StoreId storeId = new StoreId(UUID.randomUUID());

    Instant addedAt =
        Instant.parse("2026-08-01T12:00:00Z");

    organization.addStore(
        storeId,
        new StoreCode("DAKAR-01"),
        new StoreName("Dakar Store"),
        UUID.randomUUID(),
        addedAt
    );

    assertThat(organization.stores())
        .hasSize(2)
        .anySatisfy(store -> {
          assertThat(store.id()).isEqualTo(storeId);
          assertThat(store.createdAt()).isEqualTo(addedAt);
          assertThat(store.isActive()).isTrue();
          assertThat(store.isHeadquarters()).isFalse();
        });
  }

  private static Organization newOrganization() {
    return Organization.register(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        CurrencyCode.xof(),
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        Instant.parse("2026-08-01T12:00:00Z")
    );
  }
  @Test
  void shouldRegisterOrganizationRegisteredEvent() {
    UUID eventId = UUID.fromString(
        "f84f8ce3-803f-45a5-a552-d7313f9f7cf8"
    );

    Organization organization = Organization.register(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        CurrencyCode.xof(),
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        eventId,
        Instant.parse("2026-08-02T10:00:00Z")
    );

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(OrganizationRegistered.class);

    OrganizationRegistered event =
        (OrganizationRegistered)
            organization.domainEvents().getFirst();

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.organizationId())
        .isEqualTo(organization.id());
    assertThat(event.tenantId())
        .isEqualTo(organization.tenantId());
    assertThat(event.eventType())
        .isEqualTo("organization.registered.v1");
  }
  @Test
  void shouldPullAndClearDomainEvents() {
    Organization organization = newOrganization();

    List<DomainEvent> events =
        organization.pullDomainEvents();

    assertThat(events)
        .hasSize(1)
        .first()
        .isInstanceOf(OrganizationRegistered.class);

    assertThat(organization.domainEvents()).isEmpty();
  }
  @Test
  void shouldContainEventImmediatelyAfterRegistration() {
    UUID eventId = UUID.fromString(
        "f84f8ce3-803f-45a5-a552-d7313f9f7cf8"
    );

    Organization organization = Organization.register(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        CurrencyCode.xof(),
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        eventId,
        Instant.parse("2026-08-02T10:00:00Z")
    );

    assertThat(organization.domainEvents()).hasSize(1);
  }

  @Test
  void shouldRejectAddingStoreWhenOrganizationIsNotActive() {
    Organization organization = newOrganization();
    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.addStore(
        new StoreId(UUID.randomUUID()),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T20:00:00Z")
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

    assertThat(organization.stores()).hasSize(1);
    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldChangeHeadquarters() {
    Organization organization = activeOrganization();

    StoreId newStoreId = new StoreId(UUID.randomUUID());

    organization.addStore(
        newStoreId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    );

    organization.clearDomainEvents();

    UUID eventId = UUID.randomUUID();
    Instant changedAt =
        Instant.parse("2026-08-02T11:00:00Z");

    organization.changeHeadquarters(
        newStoreId,
        eventId,
        changedAt
    );

    assertThat(organization.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store ->
            assertThat(store.id()).isEqualTo(newStoreId)
        );

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(HeadquartersChanged.class);
  }

  @Test
  void shouldRejectUnknownHeadquartersStore() {
    Organization organization = activeOrganization();

    organization.clearDomainEvents();

    assertThatThrownBy(() ->
        organization.changeHeadquarters(
            new StoreId(UUID.randomUUID()),
            UUID.randomUUID(),
            Instant.parse("2026-08-02T11:00:00Z")
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
                  "STORE_DOES_NOT_BELONG_TO_ORGANIZATION"
              );
        });

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectStoreWhenItIsAlreadyHeadquarters() {
    Organization organization = activeOrganization();

    organization.clearDomainEvents();

    StoreId currentHeadquartersId = organization.stores()
        .stream()
        .filter(Store::isHeadquarters)
        .map(Store::id)
        .findFirst()
        .orElseThrow();

    assertThatThrownBy(() -> organization.changeHeadquarters(
        currentHeadquartersId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T11:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("STORE_IS_ALREADY_HEADQUARTERS");

          assertThat(exception.ruleName())
              .isEqualTo(
                  "NewHeadquartersMustBeDifferentRule"
              );

          assertThat(exception.getMessage())
              .contains("is already the headquarters");
        });

    assertThat(organization.domainEvents()).isEmpty();

    assertThat(organization.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store ->
            assertThat(store.id())
                .isEqualTo(currentHeadquartersId)
        );
  }

  @Test
  void shouldDeactivateSecondaryStore() {
    Organization organization = activeOrganization();

    StoreId secondaryStoreId = new StoreId(UUID.randomUUID());

    organization.addStore(
        secondaryStoreId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    );

    organization.clearDomainEvents();

    UUID eventId = UUID.fromString(
        "4bd292f0-d945-43dd-982c-ddd6b966bf17"
    );

    Instant deactivatedAt =
        Instant.parse("2026-08-02T12:00:00Z");

    organization.deactivateStore(
        secondaryStoreId,
        eventId,
        deactivatedAt
    );

    Store deactivatedStore = organization.stores()
        .stream()
        .filter(store -> store.id().equals(secondaryStoreId))
        .findFirst()
        .orElseThrow();

    assertThat(deactivatedStore.isActive()).isFalse();

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(StoreDeactivated.class);

    StoreDeactivated event = (StoreDeactivated)
        organization.domainEvents().getFirst();

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.organizationId()).isEqualTo(organization.id());
    assertThat(event.tenantId()).isEqualTo(organization.tenantId());
    assertThat(event.storeId()).isEqualTo(secondaryStoreId);
    assertThat(event.occurredAt()).isEqualTo(deactivatedAt);
  }

  @Test
  void shouldRejectDeactivationOfHeadquarters() {
    Organization organization = activeOrganization();

    StoreId headquartersId = organization.stores()
        .stream()
        .filter(Store::isHeadquarters)
        .map(Store::id)
        .findFirst()
        .orElseThrow();

    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.deactivateStore(
        headquartersId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo(
                  "HEADQUARTERS_CANNOT_BE_DEACTIVATED"
              );

          assertThat(exception.ruleName())
              .isEqualTo(
                  "HeadquartersCannotBeDeactivatedRule"
              );
        });

    Store headquarters = organization.stores()
        .stream()
        .filter(store -> store.id().equals(headquartersId))
        .findFirst()
        .orElseThrow();

    assertThat(headquarters.isActive()).isTrue();
    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectDeactivationOfUnknownStore() {
    Organization organization = activeOrganization();

    organization.clearDomainEvents();

    StoreId unknownStoreId = new StoreId(UUID.randomUUID());

    assertThatThrownBy(() -> organization.deactivateStore(
        unknownStoreId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z")
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

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectStoreDeactivationWhenOrganizationIsNotActive() {
    Organization organization = newOrganization();

    organization.clearDomainEvents();

    StoreId headquartersId = organization.stores()
        .stream()
        .filter(Store::isHeadquarters)
        .map(Store::id)
        .findFirst()
        .orElseThrow();

    assertThatThrownBy(() -> organization.deactivateStore(
        headquartersId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo(
                  "ORGANIZATION_MUST_BE_ACTIVE_TO_DEACTIVATE_STORE"
              );
        });

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectAlreadyInactiveStore() {
    Organization organization = activeOrganization();

    StoreId secondaryStoreId = new StoreId(UUID.randomUUID());

    organization.addStore(
        secondaryStoreId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    );

    organization.clearDomainEvents();

    organization.deactivateStore(
        secondaryStoreId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T11:00:00Z")
    );

    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.deactivateStore(
        secondaryStoreId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("STORE_ALREADY_INACTIVE");

          assertThat(exception.ruleName())
              .isEqualTo(
                  "StoreMustBeActiveToBeDeactivatedRule"
              );
        });

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldCloseActiveOrganization() {
    Organization organization = activeOrganization();

    UUID eventId = UUID.fromString(
        "0b4410bd-25f2-4ab5-88c9-1d83a989eb84"
    );

    Instant closedAt =
        Instant.parse("2026-08-03T10:00:00Z");

    organization.close(eventId, closedAt);

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.CLOSED);

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(OrganizationClosed.class);

    OrganizationClosed event =
        (OrganizationClosed)
            organization.domainEvents().getFirst();

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.organizationId())
        .isEqualTo(organization.id());
    assertThat(event.tenantId())
        .isEqualTo(organization.tenantId());
    assertThat(event.occurredAt())
        .isEqualTo(closedAt);
  }

  @Test
  void shouldCloseSuspendedOrganization() {
    Organization organization = activeOrganization();

    organization.suspend(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T09:00:00Z")
    );

    organization.clearDomainEvents();

    organization.close(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
    );

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.CLOSED);

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(OrganizationClosed.class);
  }

  @Test
  void shouldRejectClosingPendingOrganization() {
    Organization organization = newOrganization();
    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.close(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
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

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectClosingAlreadyClosedOrganization() {
    Organization organization = activeOrganization();

    organization.close(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
    );

    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.close(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T11:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("ORGANIZATION_ALREADY_CLOSED");
        });

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.CLOSED);

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRegisterOrganizationAndHeadquartersAtSameTime() {
    Instant createdAt =
        Instant.parse("2026-08-01T12:00:00Z");

    Organization organization = Organization.register(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        CurrencyCode.xof(),
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        createdAt
    );

    Store headquarters = organization.stores()
        .stream()
        .filter(Store::isHeadquarters)
        .findFirst()
        .orElseThrow();

    assertThat(organization.createdAt())
        .isEqualTo(createdAt);

    assertThat(headquarters.createdAt())
        .isEqualTo(createdAt);
  }

  @Test
  void shouldRejectNullLegalNameOnRegister() {
    assertThatThrownBy(() -> Organization.register(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        null,
        CurrencyCode.xof(),
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        Instant.parse("2026-08-01T12:00:00Z")
    ))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Legal name cannot be null");
  }

  @Test
  void shouldRejectNullLegalNameOnRestore() {
    assertThatThrownBy(() -> restoreWithStores(
        null,
        List.of(headquartersStore("THIES-01"))
    ))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Legal name cannot be null");
  }

  @Test
  void shouldSuspendActiveOrganization() {
    Organization organization = activeOrganization();

    UUID eventId = UUID.fromString(
        "9a0e8a51-43c4-4f8b-9a47-5b0c0d2a1f10"
    );

    Instant suspendedAt =
        Instant.parse("2026-08-03T09:00:00Z");

    organization.suspend(eventId, suspendedAt);

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.SUSPENDED);

    assertThat(organization.domainEvents())
        .singleElement()
        .isInstanceOf(OrganizationSuspended.class);

    OrganizationSuspended event =
        (OrganizationSuspended)
            organization.domainEvents().getFirst();

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.organizationId())
        .isEqualTo(organization.id());
    assertThat(event.tenantId())
        .isEqualTo(organization.tenantId());
    assertThat(event.occurredAt())
        .isEqualTo(suspendedAt);
    assertThat(event.eventType())
        .isEqualTo("organization.suspended.v1");
  }

  @Test
  void shouldRejectSuspendingPendingOrganization() {
    Organization organization = newOrganization();
    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.suspend(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T09:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("ORGANIZATION_CANNOT_BE_SUSPENDED");

          assertThat(exception.ruleName())
              .isEqualTo(
                  "OrganizationMustBeActiveToBeSuspendedRule"
              );
        });

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.PENDING_ACTIVATION);

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectSuspendingAlreadySuspendedOrganization() {
    Organization organization = activeOrganization();

    organization.suspend(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T09:00:00Z")
    );

    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.suspend(
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable ->
            assertThat(
                ((BusinessRuleViolationException) throwable).code()
            ).isEqualTo("ORGANIZATION_CANNOT_BE_SUSPENDED")
        );

    assertThat(organization.status())
        .isEqualTo(OrganizationStatus.SUSPENDED);

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectDuplicateStoreId() {
    Organization organization = activeOrganization();

    StoreId existingStoreId = organization.stores()
        .getFirst()
        .id();

    assertThatThrownBy(() -> organization.addStore(
        existingStoreId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo("STORE_ID_ALREADY_EXISTS");

          assertThat(exception.ruleName())
              .isEqualTo("StoreIdMustBeUniqueRule");
        });

    assertThat(organization.stores()).hasSize(1);
    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectInactiveStoreAsNewHeadquarters() {
    Organization organization = activeOrganization();

    StoreId currentHeadquartersId = organization.stores()
        .getFirst()
        .id();

    StoreId secondaryStoreId = new StoreId(UUID.randomUUID());

    organization.addStore(
        secondaryStoreId,
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T10:00:00Z")
    );

    organization.deactivateStore(
        secondaryStoreId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T11:00:00Z")
    );

    organization.clearDomainEvents();

    assertThatThrownBy(() -> organization.changeHeadquarters(
        secondaryStoreId,
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z")
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .satisfies(throwable -> {
          BusinessRuleViolationException exception =
              (BusinessRuleViolationException) throwable;

          assertThat(exception.code())
              .isEqualTo(
                  "INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS"
              );

          assertThat(exception.ruleName())
              .isEqualTo(
                  "StoreMustBeActiveToBecomeHeadquartersRule"
              );
        });

    assertThat(organization.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .satisfies(store ->
            assertThat(store.id())
                .isEqualTo(currentHeadquartersId)
        );

    assertThat(organization.domainEvents()).isEmpty();
  }

  @Test
  void shouldRejectRestoreWithoutHeadquarters() {
    assertThatThrownBy(() -> restoreWithStores(
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        List.of(secondaryStore("THIES-01"))
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Organization must contain exactly one headquarters"
        );
  }

  @Test
  void shouldRejectRestoreWithTwoHeadquarters() {
    assertThatThrownBy(() -> restoreWithStores(
        new LegalName("QUINCAILLERIE THIÈS SARL"),
        List.of(
            headquartersStore("THIES-01"),
            headquartersStore("DAKAR-01")
        )
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Organization must contain exactly one headquarters"
        );
  }

  private static Organization restoreWithStores(
      LegalName legalName,
      List<Store> stores
  ) {
    return Organization.restore(
        new OrganizationId(UUID.randomUUID()),
        new TenantId(UUID.randomUUID()),
        new OrganizationName("Quincaillerie Thiès"),
        legalName,
        CurrencyCode.xof(),
        OrganizationStatus.ACTIVE,
        stores,
        Instant.parse("2026-08-01T12:00:00Z"),
        0L
    );
  }

  private static Store headquartersStore(String code) {
    return Store.restore(
        new StoreId(UUID.randomUUID()),
        new StoreCode(code),
        new StoreName("Magasin " + code),
        true,
        true,
        Instant.parse("2026-08-01T12:00:00Z")
    );
  }

  private static Store secondaryStore(String code) {
    return Store.restore(
        new StoreId(UUID.randomUUID()),
        new StoreCode(code),
        new StoreName("Magasin " + code),
        false,
        true,
        Instant.parse("2026-08-01T12:00:00Z")
    );
  }

  private static Organization activeOrganization() {
    Organization organization = newOrganization();

    organization.clearDomainEvents();

    organization.activate(
        UUID.randomUUID(),
        Instant.parse("2026-08-01T11:00:00Z")
    );

    organization.clearDomainEvents();

    return organization;
  }
}