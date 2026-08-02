package com.zim.organization.domain.event;

import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationRegistered (
        UUID eventId,
        OrganizationId organizationId,
        TenantId tenantId,
        StoreId headquartersId,
        Instant occurredAt
) implements DomainEvent {

    public static final String TYPE = "organization.registered.v1";

    public OrganizationRegistered {
        Objects.requireNonNull(eventId, "Event id cannot be null");
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(
                headquartersId,
                "Headquarters id cannot be null"
        );
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
