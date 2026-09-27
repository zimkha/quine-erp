package com.zim.organization.infrastructure.persistence.mapper;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.*;
import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.zim.organization.infrastructure.persistence.entity.StoreEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OrganizationPersistenceMapper {

  public OrganizationEntity toEntity(
      Organization organization
  ) {
    Objects.requireNonNull(
        organization,
        "Organization cannot be null"
    );

    OrganizationEntity entity =
        new OrganizationEntity(
            organization.id().value(),
            organization.tenantId().value(),
            organization.name().value(),
            organization.legalName().value(),
            organization.legalName().normalizedValue(),
            organization.currency().value(),
            organization.status().name(),
            organization.createdAt()
        );

    organization.stores()
        .stream()
        .map(this::toEntity)
        .forEach(entity::addStore);

    return entity;
  }

  public Organization toDomain(
      OrganizationEntity entity
  ) {
    Objects.requireNonNull(
        entity,
        "Organization entity cannot be null"
    );

    List<Store> stores = entity.getStores()
        .stream()
        .map(this::toDomain)
        .toList();

    return Organization.restore(
        new OrganizationId(entity.getId()),
        new TenantId(entity.getTenantId()),
        new OrganizationName(entity.getName()),
        new LegalName(entity.getLegalName()),
        new CurrencyCode(entity.getCurrency()),
        OrganizationStatus.valueOf(entity.getStatus()),
        stores,
        entity.getCreatedAt(),
        entity.getVersion()
    );
  }

  /**
   * Clears the headquarters flag on every managed store that is no longer
   * the headquarters of the given aggregate.
   *
   * @return {@code true} if at least one store was released
   */
  public boolean releaseFormerHeadquarters(
      Organization source,
      OrganizationEntity target
  ) {
    Set<UUID> headquartersIds = source.stores()
        .stream()
        .filter(Store::isHeadquarters)
        .map(store -> store.id().value())
        .collect(Collectors.toSet());

    boolean released = false;

    for (StoreEntity store : target.getStores()) {
      if (store.isHeadquarters()
          && !headquartersIds.contains(store.getId())) {
        store.releaseHeadquarters();
        released = true;
      }
    }

    return released;
  }

  /**
   * Copies the mutable state of the aggregate onto its managed entity:
   * status, existing stores, and stores added since it was loaded.
   */
  public void copyState(
      Organization source,
      OrganizationEntity target
  ) {
    Objects.requireNonNull(source, "Organization cannot be null");
    Objects.requireNonNull(target, "Organization entity cannot be null");

    target.changeStatus(source.status().name());

    Map<UUID, StoreEntity> existingStores = target.getStores()
        .stream()
        .collect(Collectors.toMap(
            StoreEntity::getId,
            Function.identity()
        ));

    for (Store store : source.stores()) {
      StoreEntity existing = existingStores.get(store.id().value());

      if (existing == null) {
        target.addStore(toEntity(store));
      } else {
        existing.update(
            store.code().value(),
            store.name().value(),
            store.isHeadquarters(),
            store.isActive()
        );
      }
    }
  }

  private StoreEntity toEntity(Store store) {
    return new StoreEntity(
        store.id().value(),
        store.code().value(),
        store.name().value(),
        store.isHeadquarters(),
        store.isActive(),
        store.createdAt()
    );
  }

  private Store toDomain(StoreEntity entity) {
    return Store.restore(
        new StoreId(entity.getId()),
        new StoreCode(entity.getCode()),
        new StoreName(entity.getName()),
        entity.isHeadquarters(),
        entity.isActive(),
        entity.getCreatedAt()
    );
  }
}