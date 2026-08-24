package com.zim.organization.domain.model;

import com.zim.organization.domain.event.*;
import com.zim.organization.domain.rule.*;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.AggregateRoot;

import com.zim.organization.domain.rule.HeadquartersCannotBeDeactivatedRule;
import com.zim.organization.domain.rule.OrganizationMustBeActiveToDeactivateStoreRule;
import com.zim.organization.domain.rule.StoreMustBeActiveToBeDeactivatedRule;
import com.zim.organization.domain.rule.OrganizationMustKeepAtLeastOneActiveStoreRule;




import java.time.Instant;
import java.util.*;

public final class Organization  extends AggregateRoot {

    private final OrganizationId id;
    private final TenantId tenantId;
    private final OrganizationName name;
    private final LegalName legalName;
    private final CurrencyCode currency;
    private OrganizationStatus status;
    private final List<Store> stores;
    private final Instant createdAt;


    private Organization(
            OrganizationId id,
            TenantId tenantId,
            OrganizationName name,
            LegalName legalName,
            CurrencyCode currency,
            OrganizationStatus status,
            List<Store> stores,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "Organization id cannot be null");
        this.tenantId = Objects.requireNonNull(
                tenantId,
                "Tenant id cannot be null"
        );
        this.name = Objects.requireNonNull(
                name,
                "Organization name cannot be null"
        );
        this.legalName = legalName;
        this.currency = Objects.requireNonNull(
                currency,
                "Currency cannot be null"
        );
        this.status = Objects.requireNonNull(
                status,
                "Organization status cannot be null"
        );
        this.stores = new ArrayList<>(
                Objects.requireNonNull(stores, "Stores cannot be null")
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Created at cannot be null"
        );

        ensureAtLeastOneStore();
        ensureExactlyOneHeadquarters();
    }

    public static Organization register(
            OrganizationId organizationId,
            TenantId tenantId,
            OrganizationName organizationName,
            LegalName legalName,
            CurrencyCode currency,
            StoreId headquartersId,
            StoreCode headquartersCode,
            StoreName headquartersName,
            UUID eventId,
            Instant createdAt
    ) {
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Store headquarters = Store.createHeadquarters(
                headquartersId,
                headquartersCode,
                headquartersName,
                createdAt
        );

        Organization organization = new Organization(

                organizationId,
                tenantId,
                organizationName,
                legalName,
                currency,
                OrganizationStatus.PENDING_ACTIVATION,
                List.of(headquarters),
                createdAt
        );

        organization.registerEvent(
                new OrganizationRegistered(
                        eventId,
                        organizationId,
                        tenantId,
                        headquartersId,
                        createdAt
                )
        );
        return organization;
    }

    public void activate(UUID eventId, Instant occurredAt) {
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );

        checkRule(
                new OrganizationMustBeActivatableRule(status)
        );

        status = OrganizationStatus.ACTIVE;

        registerEvent(
                new OrganizationActivated(
                        eventId,
                        id,
                        tenantId,
                        occurredAt
                )
        );
    }

    public void suspend() {
        ensureStatus(OrganizationStatus.ACTIVE);
        status = OrganizationStatus.SUSPENDED;
    }

    public void close(
            UUID eventId,
            Instant occurredAt
    ) {
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );

        checkRule(
                new OrganizationMustBeClosableRule(status)
        );

        status = OrganizationStatus.CLOSED;

        registerEvent(
                new OrganizationClosed(
                        eventId,
                        id,
                        tenantId,
                        occurredAt
                )
        );
    }

    public void addStore(
            StoreId storeId,
            StoreCode storeCode,
            StoreName storeName,
            UUID eventId,
            Instant occurredAt
    ) {
        Objects.requireNonNull(storeId, "Store id cannot be null");
        Objects.requireNonNull(storeCode, "Store code cannot be null");
        Objects.requireNonNull(storeName, "Store name cannot be null");
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );

        checkRule(
                new OrganizationMustBeActiveToAddStoreRule(status)
        );

        checkRule(
                new StoreCodeMustBeUniqueRule(stores, storeCode)
        );

        boolean duplicateId = stores.stream()
                .anyMatch(store -> store.id().equals(storeId));

        if (duplicateId) {
            throw new IllegalArgumentException(
                    "A store with id '%s' already exists"
                            .formatted(storeId)
            );
        }

        stores.add(
                Store.create(
                        storeId,
                        storeCode,
                        storeName,
                        occurredAt
                )
        );

        registerEvent(
                new StoreAdded(
                        eventId,
                        id,
                        tenantId,
                        storeId,
                        storeCode,
                        occurredAt
                )
        );
    }

    public void changeHeadquarters(
            StoreId newHeadquartersId,
            UUID eventId,
            Instant occurredAt
    ) {
        Objects.requireNonNull(
                newHeadquartersId,
                "New headquarters id cannot be null"
        );
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );

        checkRule(
                new OrganizationMustBeActiveToChangeHeadquartersRule(status)
        );

        checkRule(
                new StoreMustBelongToOrganizationRule(
                        stores,
                        newHeadquartersId
                )
        );

        Store currentHeadquarters = stores.stream()
                .filter(Store::isHeadquarters)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Organization has no headquarters"
                ));

        checkRule(
                new NewHeadquartersMustBeDifferentRule(
                        currentHeadquarters.id(),
                        newHeadquartersId
                )
        );

        Store newHeadquarters = stores.stream()
                .filter(store -> store.id().equals(newHeadquartersId))
                .findFirst()
                .orElseThrow();

        checkRule(
                new StoreMustBeActiveToBecomeHeadquartersRule(
                        newHeadquarters
                )
        );

        currentHeadquarters.removeHeadquartersStatus();
        newHeadquarters.markAsHeadquarters();

        ensureExactlyOneHeadquarters();

        registerEvent(
                new HeadquartersChanged(
                        eventId,
                        id,
                        tenantId,
                        currentHeadquarters.id(),
                        newHeadquarters.id(),
                        occurredAt
                )
        );
    }

    public static Organization restore(
            OrganizationId organizationId,
            TenantId tenantId,
            OrganizationName organizationName,
            LegalName legalName,
            CurrencyCode currency,
            OrganizationStatus status,
            List<Store> stores,
            Instant createdAt
    ) {
        return new Organization(
                organizationId,
                tenantId,
                organizationName,
                legalName,
                currency,
                status,
                stores,
                createdAt
        );
    }

    public void deactivateStore(
            StoreId storeId,
            UUID eventId,
            Instant occurredAt
    ) {
        Objects.requireNonNull(storeId, "Store id cannot be null");
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );

        checkRule(
                new OrganizationMustBeActiveToDeactivateStoreRule(status)
        );

        checkRule(
                new StoreMustBelongToOrganizationRule(
                        stores,
                        storeId
                )
        );

        Store store = stores.stream()
                .filter(candidate -> candidate.id().equals(storeId))
                .findFirst()
                .orElseThrow();

        checkRule(
                new HeadquartersCannotBeDeactivatedRule(store)
        );

        checkRule(
                new StoreMustBeActiveToBeDeactivatedRule(store)
        );

        checkRule(
                new OrganizationMustKeepAtLeastOneActiveStoreRule(
                        stores,
                        store
                )
        );

        store.deactivate();

        registerEvent(
                new StoreDeactivated(
                        eventId,
                        id,
                        tenantId,
                        store.id(),
                        occurredAt
                )
        );
    }

    private void ensureNotClosed() {
        if (status == OrganizationStatus.CLOSED) {
            throw new IllegalStateException(
                    "Closed organization cannot be modified"
            );
        }
    }

    private void ensureStatus(OrganizationStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "Expected organization status " + expectedStatus
                            + " but was " + status
            );
        }
    }

    private void ensureAtLeastOneStore() {
        if (stores.isEmpty()) {
            throw new IllegalArgumentException(
                    "Organization must contain at least one store"
            );
        }
    }

    private void ensureExactlyOneHeadquarters() {
        long headquartersCount = stores.stream()
                .filter(Store::isHeadquarters)
                .count();

        if (headquartersCount != 1) {
            throw new IllegalArgumentException(
                    "Organization must contain exactly one headquarters"
            );
        }
    }


    public OrganizationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public OrganizationName name() {
        return name;
    }

    public LegalName legalName() {
        return legalName;
    }
    public CurrencyCode currency() {
        return currency;
    }

    public OrganizationStatus status() {
        return status;
    }

    public List<Store> stores() {
        return List.copyOf(stores);
    }

    public Instant createdAt() {
        return createdAt;
    }
}