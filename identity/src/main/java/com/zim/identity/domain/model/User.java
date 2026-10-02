package com.zim.identity.domain.model;

import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.AggregateRoot;
import com.zim.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * A person who can sign in. A user belongs to exactly one tenant (PO
 * decision 5), and the user created at registration is the tenant's owner.
 */
public final class User extends AggregateRoot {

  private final UserId id;
  private final TenantId tenantId;
  private final Email email;
  private final PasswordHash passwordHash;
  private final boolean owner;
  private final Instant createdAt;

  private User(
      UserId id,
      TenantId tenantId,
      Email email,
      PasswordHash passwordHash,
      boolean owner,
      Instant createdAt
  ) {
    this.id = Objects.requireNonNull(id, "User id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    this.email = Objects.requireNonNull(email, "E-mail cannot be null");
    this.passwordHash =
        Objects.requireNonNull(passwordHash, "Password hash cannot be null");
    this.owner = owner;
    this.createdAt =
        Objects.requireNonNull(createdAt, "Created at cannot be null");
  }

  public static User createOwner(
      UserId id,
      TenantId tenantId,
      Email email,
      PasswordHash passwordHash,
      Instant createdAt
  ) {
    return new User(id, tenantId, email, passwordHash, true, createdAt);
  }

  /** Rebuilds a stored user. No rule runs: it was valid when saved. */
  public static User restore(
      UserId id,
      TenantId tenantId,
      Email email,
      PasswordHash passwordHash,
      boolean owner,
      Instant createdAt
  ) {
    return new User(id, tenantId, email, passwordHash, owner, createdAt);
  }

  public UserId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public Email email() {
    return email;
  }

  public PasswordHash passwordHash() {
    return passwordHash;
  }

  public boolean isOwner() {
    return owner;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
