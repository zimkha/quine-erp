package com.zim.identity.infrastructure.persistence.adapteur;

import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.rule.EmailMustBeUniqueRule;
import com.zim.identity.domain.rule.TenantMustHaveSingleOwnerRule;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.zim.identity.infrastructure.persistence.repository.SpringDataUserRepository;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.TenantId;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Objects;
import java.util.Optional;

/**
 * JPA implementation of {@link UserRepository}. Meant to run inside the
 * caller's transaction (see {@code IdentityConfiguration}).
 */
public final class UserRepositoryAdapter implements UserRepository {

  // Both are unique indexes; Postgres reports an index name as the constraint.
  static final String UNIQUE_EMAIL = "uk_users_email";
  static final String UNIQUE_OWNER_PER_TENANT = "uk_users_one_owner_per_tenant";

  private final SpringDataUserRepository repository;
  private final UserPersistenceMapper mapper;

  public UserRepositoryAdapter(
      SpringDataUserRepository repository,
      UserPersistenceMapper mapper
  ) {
    this.repository = Objects.requireNonNull(repository);
    this.mapper = Objects.requireNonNull(mapper);
  }

  @Override
  public void save(User user) {
    Objects.requireNonNull(user, "User cannot be null");

    try {
      // Flush now so a unique violation surfaces here, where it can be
      // translated, rather than at commit time.
      repository.saveAndFlush(mapper.toEntity(user));
    } catch (DataIntegrityViolationException exception) {
      if (violates(exception, UNIQUE_EMAIL)) {
        throw new BusinessRuleViolationException(
            new EmailMustBeUniqueRule(user.email(), true)
        );
      }
      if (violates(exception, UNIQUE_OWNER_PER_TENANT)) {
        throw new BusinessRuleViolationException(
            new TenantMustHaveSingleOwnerRule(true)
        );
      }
      throw exception;
    }
  }

  @Override
  public boolean existsByEmail(Email email) {
    return repository.existsByEmail(email.value());
  }

  @Override
  public boolean existsOwnerByTenantId(TenantId tenantId) {
    return repository.existsByTenantIdAndOwnerTrue(tenantId.value());
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return repository.findByEmail(email.value()).map(mapper::toDomain);
  }

  private static boolean violates(
      DataIntegrityViolationException exception,
      String constraintName
  ) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return constraintName.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }
}
