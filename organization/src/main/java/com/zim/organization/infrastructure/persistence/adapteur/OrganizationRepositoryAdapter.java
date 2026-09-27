package com.zim.organization.infrastructure.persistence.adapteur;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.shared.domain.TenantId;
import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * JPA implementation of {@link OrganizationRepository}.
 *
 * <p>{@link #save(Organization)} must run inside a transaction that also
 * covers the preceding load (see {@code OrganizationConfiguration}).
 *
 * <p>Call {@link #save(Organization)} at most once per aggregate per
 * transaction: each update forces a version increment and checks the
 * aggregate's loaded version, which a second save of the same instance no
 * longer matches, so it fails as an optimistic locking conflict.
 */
public final class OrganizationRepositoryAdapter
    implements OrganizationRepository {

  private static final String UNIQUE_LEGAL_NAME_CONSTRAINT =
      "uk_organizations_normalized_legal_name";

  private final SpringDataOrganizationRepository repository;
  private final OrganizationPersistenceMapper mapper;
  private final EntityManager entityManager;

  public OrganizationRepositoryAdapter(
      SpringDataOrganizationRepository repository,
      OrganizationPersistenceMapper mapper,
      EntityManager entityManager
  ) {
    this.repository = Objects.requireNonNull(repository);
    this.mapper = Objects.requireNonNull(mapper);
    this.entityManager = Objects.requireNonNull(entityManager);
  }

  @Override
  public void save(Organization organization) {
    Objects.requireNonNull(organization, "Organization cannot be null");

    if (organization.version() == null) {
      insert(organization);
    } else {
      update(organization);
    }
  }

  private void insert(Organization organization) {
    try {
      // Flush now so a unique-constraint violation surfaces here, where it
      // can be translated, rather than at commit time.
      repository.saveAndFlush(mapper.toEntity(organization));
    } catch (DataIntegrityViolationException exception) {
      if (violates(exception, UNIQUE_LEGAL_NAME_CONSTRAINT)) {
        throw new OrganizationAlreadyExistsException(
            organization.legalName().value()
        );
      }
      throw exception;
    }
  }

  private void update(Organization organization) {
    OrganizationEntity entity = repository
        .findWithStoresByIdAndTenantId(
            organization.id().value(),
            organization.tenantId().value()
        )
        .orElseThrow(() -> staleState(organization));

    if (!entity.getVersion().equals(organization.version())) {
      throw staleState(organization);
    }

    // Bump the root version before touching any row, even if only stores
    // change. The UPDATE takes the row lock, so a concurrent writer of the
    // same aggregate is rejected here instead of racing on the stores.
    forceVersionIncrement(entity);

    // uk_stores_one_headquarters_per_organization is a partial unique index,
    // so Postgres checks it per row and it cannot be deferred. Hibernate does
    // not guarantee UPDATE order, so the former headquarters is released and
    // flushed before the new one is flagged.
    if (mapper.releaseFormerHeadquarters(organization, entity)) {
      repository.flush();
    }

    mapper.copyState(organization, entity);
  }

  private void forceVersionIncrement(OrganizationEntity entity) {
    try {
      entityManager.lock(
          entity,
          LockModeType.PESSIMISTIC_FORCE_INCREMENT
      );
    } catch (RuntimeException exception) {
      DataAccessException translated =
          EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(
              exception
          );
      throw translated != null ? translated : exception;
    }
  }

  private static ObjectOptimisticLockingFailureException staleState(
      Organization organization
  ) {
    return new ObjectOptimisticLockingFailureException(
        OrganizationEntity.class,
        organization.id().value()
    );
  }

  private static boolean violates(
      DataIntegrityViolationException exception,
      String constraintName
  ) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return constraintName.equalsIgnoreCase(
            violation.getConstraintName()
        );
      }
    }
    return false;
  }

  @Override
  public Optional<Organization> findById(
      TenantId tenantId,
      OrganizationId organizationId
  ) {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(organizationId);

    return repository
        .findWithStoresByIdAndTenantId(
            organizationId.value(),
            tenantId.value()
        )
        .map(mapper::toDomain);
  }

  @Override
  public boolean existsByLegalName(String normalizedLegalName) {
    Objects.requireNonNull(normalizedLegalName);

    return repository.existsByNormalizedLegalName(
        normalizedLegalName
    );
  }
}
