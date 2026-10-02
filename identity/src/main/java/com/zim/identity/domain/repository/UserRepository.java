package com.zim.identity.domain.repository;

import com.zim.identity.domain.model.User;
import com.zim.identity.domain.valueobject.Email;
import com.zim.shared.domain.TenantId;

import java.util.Optional;

public interface UserRepository {

  /**
   * Inserts a new user. A race on the unique e-mail or on the single owner
   * is reported with the same {@code BusinessRuleViolationException} as the
   * checks made before saving.
   */
  void save(User user);

  boolean existsByEmail(Email email);

  boolean existsOwnerByTenantId(TenantId tenantId);

  Optional<User> findByEmail(Email email);
}
