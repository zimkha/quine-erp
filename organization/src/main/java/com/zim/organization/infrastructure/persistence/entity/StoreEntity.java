package com.zim.organization.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "stores",
    schema = "organization",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_stores_organization_code",
            columnNames = {
                "organization_id",
                "code"
            }
        )
    }
)
public class StoreEntity {

  @Id
  @Column(
      name = "id",
      nullable = false,
      updatable = false
  )
  private UUID id;

  @ManyToOne(
      fetch = FetchType.LAZY,
      optional = false
  )
  @JoinColumn(
      name = "organization_id",
      nullable = false
  )
  private OrganizationEntity organization;

  // Copied from the organization in attachTo. The database enforces the
  // match with fk_stores_organization_tenant (organization_id, tenant_id).
  @Column(
      name = "tenant_id",
      nullable = false,
      updatable = false
  )
  private UUID tenantId;

  @Column(
      name = "code",
      nullable = false,
      length = 20
  )
  private String code;

  @Column(
      name = "name",
      nullable = false,
      length = 120
  )
  private String name;

  @Column(
      name = "headquarters",
      nullable = false
  )
  private boolean headquarters;

  @Column(
      name = "active",
      nullable = false
  )
  private boolean active;

  @Column(
      name = "created_at",
      nullable = false,
      updatable = false
  )
  private Instant createdAt;

  protected StoreEntity() {
    // Required by JPA
  }

  public StoreEntity(
      UUID id,
      String code,
      String name,
      boolean headquarters,
      boolean active,
      Instant createdAt
  ) {
    this.id = Objects.requireNonNull(id);
    this.code = Objects.requireNonNull(code);
    this.name = Objects.requireNonNull(name);
    this.headquarters = headquarters;
    this.active = active;
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  void attachTo(OrganizationEntity organization) {
    this.organization = Objects.requireNonNull(
        organization,
        "Organization cannot be null"
    );
    this.tenantId = Objects.requireNonNull(
        organization.getTenantId(),
        "Organization tenant cannot be null"
    );
  }

  public void releaseHeadquarters() {
    this.headquarters = false;
  }

  public void update(
      String code,
      String name,
      boolean headquarters,
      boolean active
  ) {
    this.code = Objects.requireNonNull(code);
    this.name = Objects.requireNonNull(name);
    this.headquarters = headquarters;
    this.active = active;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isHeadquarters() {
    return headquarters;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}