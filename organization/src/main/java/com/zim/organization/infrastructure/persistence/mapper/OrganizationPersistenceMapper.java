package com.zim.organization.infrastructure.persistence.mapper;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.*;
import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.zim.organization.infrastructure.persistence.entity.StoreEntity;

import java.util.List;
import java.util.Objects;

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
                entity.getCreatedAt()
        );
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