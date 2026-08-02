package com.zim.organization.domain.model;

import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;

import java.util.Objects;

public final class Store {

    private final StoreId id;
    private StoreCode code;
    private StoreName name;
    private boolean headquarters;
    private boolean active;

    private Store(
            StoreId id,
            StoreCode code,
            StoreName name,
            boolean headquarters,
            boolean active
    ) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.headquarters = headquarters;
        this.active = active;
    }

    public static Store createHeadquarters(
            StoreId id,
            StoreCode code,
            StoreName name
    ) {
        return new Store(id, code, name, true, true);
    }

    public static Store create(
            StoreId id,
            StoreCode code,
            StoreName name
    ) {
        return new Store(id, code, name, false, true);
    }

    public void rename(StoreName newName) {
        this.name = Objects.requireNonNull(newName);
    }

    public void markAsHeadquarters() {
        ensureActive();
        this.headquarters = true;
    }

    public void removeHeadquartersStatus() {
        this.headquarters = false;
    }

    public void deactivate() {
        this.active = false;
    }

    private void ensureActive() {
        if (!active) {
            throw new IllegalStateException(
                    "Inactive store cannot become headquarters"
            );
        }
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
}
