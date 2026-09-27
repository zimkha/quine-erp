package com.zim.organization.domain.event;

import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.TenantId;
import com.zim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StoreAdded(
        UUID eventId,
        OrganizationId organizationId,
        TenantId tenantId,
        StoreId storeId,
        StoreCode storeCode,
        Instant occurredAt
) implements DomainEvent {

    public static final String TYPE = "organization.store-added.v1";

    public StoreAdded {
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(storeId, "Store id cannot be null");
        Objects.requireNonNull(storeCode, "Store code cannot be null");
        Objects.requireNonNull(
                occurredAt,
                "Occurred at cannot be null"
        );
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}
