package com.zim.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "identity")
public class UserEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "owner", nullable = false, updatable = false)
  private boolean owner;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected UserEntity() {
  }

  public UserEntity(
      UUID id,
      UUID tenantId,
      String email,
      String passwordHash,
      boolean owner,
      Instant createdAt
  ) {
    this.id = id;
    this.tenantId = tenantId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.owner = owner;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isOwner() {
    return owner;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
