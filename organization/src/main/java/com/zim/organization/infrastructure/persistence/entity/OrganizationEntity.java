package com.zim.organization.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "organizations",
        schema = "organization"
)
public class OrganizationEntity {

    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "tenant_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID tenantId;

    @Column(
            name = "name",
            nullable = false,
            length = 120
    )
    private String name;

    @Column(
            name = "legal_name",
            nullable = false,
            length = 160
    )
    private String legalName;

    @Column(
            name = "normalized_legal_name",
            nullable = false,
            length = 160,
            unique = true
    )
    private String normalizedLegalName;

    @Column(
            name = "currency",
            nullable = false,
            length = 3
    )
    private String currency;

    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private String status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @OneToMany(
            mappedBy = "organization",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<StoreEntity> stores = new ArrayList<>();

    protected OrganizationEntity() {
        // Required by JPA
    }

    public OrganizationEntity(
            UUID id,
            UUID tenantId,
            String name,
            String legalName,
            String normalizedLegalName,
            String currency,
            String status,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.name = Objects.requireNonNull(name);
        this.legalName = Objects.requireNonNull(legalName);
        this.normalizedLegalName =
                Objects.requireNonNull(normalizedLegalName);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public void addStore(StoreEntity store) {
        Objects.requireNonNull(store, "Store cannot be null");

        stores.add(store);
        store.attachTo(this);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getNormalizedLegalName() {
        return normalizedLegalName;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<StoreEntity> getStores() {
        return List.copyOf(stores);
    }
}
