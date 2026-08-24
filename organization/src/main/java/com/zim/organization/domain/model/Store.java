package com.zim.organization.domain.model;

import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;

import java.time.Instant;
import java.util.Objects;

public final class Store {

    private final StoreId id;
    private StoreCode code;
    private StoreName name;
    private boolean headquarters;
    private boolean active;
    private final Instant createdAt;

    private Store(
            StoreId id,
            StoreCode code,
            StoreName name,
            boolean headquarters,
            boolean active,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Store id cannot be null"
        );
        this.code = Objects.requireNonNull(
                code,
                "Store code cannot be null"
        );
        this.name = Objects.requireNonNull(
                name,
                "Store name cannot be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Store creation date cannot be null"
        );
        this.headquarters = headquarters;
        this.active = active;
    }

    public static Store createHeadquarters(
            StoreId id,
            StoreCode code,
            StoreName name,
            Instant createdAt
    ) {
        return new Store(
                id,
                code,
                name,
                true,
                true,
                createdAt
        );
    }

    public static Store create(
            StoreId id,
            StoreCode code,
            StoreName name,
            Instant createdAt
    ) {
        return new Store(
                id,
                code,
                name,
                false,
                true,
                createdAt
        );
    }

    /**
     * Reconstruit un magasin depuis la persistance.
     * Cette méthode ne représente pas une création métier :
     * elle conserve l'état stocké en base.
     */
    public static Store restore(
            StoreId id,
            StoreCode code,
            StoreName name,
            boolean headquarters,
            boolean active,
            Instant createdAt
    ) {
        return new Store(
                id,
                code,
                name,
                headquarters,
                active,
                createdAt
        );
    }

    public void rename(StoreName newName) {
        this.name = Objects.requireNonNull(
                newName,
                "New store name cannot be null"
        );
    }

    public void changeCode(StoreCode newCode) {
        this.code = Objects.requireNonNull(
                newCode,
                "New store code cannot be null"
        );
    }

    public void markAsHeadquarters() {
        if (!active) {
            throw new IllegalStateException(
                    "Inactive store cannot become headquarters"
            );
        }

        this.headquarters = true;
    }

    public void removeHeadquartersStatus() {
        this.headquarters = false;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public StoreId id() {
        return id;
    }

    public StoreCode code() {
        return code;
    }

    public StoreName name() {
        return name;
    }

    public boolean isHeadquarters() {
        return headquarters;
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}