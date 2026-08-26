package com.zim.organization.infrastructure.configuration;

import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.handler.CloseOrganizationHandler;
import com.zim.organization.application.handler.DeactivateStoreHandler;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.port.ClockProvider;
import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.organization.application.port.EventIdGenerator;
import com.zim.organization.application.port.OrganizationIdGenerator;
import com.zim.organization.application.port.StoreIdGenerator;
import com.zim.organization.application.port.TenantIdGenerator;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.organization.infrastructure.event.SpringDomainEventPublisher;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class OrganizationConfiguration {

    @Bean
    OrganizationPersistenceMapper organizationPersistenceMapper() {
        return new OrganizationPersistenceMapper();
    }

    @Bean
    OrganizationRepository organizationRepository(
            SpringDataOrganizationRepository springDataRepository,
            OrganizationPersistenceMapper mapper
    ) {
        return new OrganizationRepositoryAdapter(
                springDataRepository,
                mapper
        );
    }

    @Bean
    OrganizationIdGenerator organizationIdGenerator() {
        return () ->
                new OrganizationId(
                        UUID.randomUUID()
                );
    }

    @Bean
    TenantIdGenerator tenantIdGenerator() {
        return () ->
                new TenantId(
                        UUID.randomUUID()
                );
    }

    @Bean
    StoreIdGenerator storeIdGenerator() {
        return () ->
                new StoreId(
                        UUID.randomUUID()
                );
    }

    @Bean
    EventIdGenerator eventIdGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    ClockProvider clockProvider() {
        return Instant::now;
    }

    @Bean
    DomainEventPublisher domainEventPublisher(
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return new SpringDomainEventPublisher(
                applicationEventPublisher
        );
    }

    @Bean
    RegisterOrganizationHandler registerOrganizationHandler(
            OrganizationRepository organizationRepository,
            OrganizationIdGenerator organizationIdGenerator,
            TenantIdGenerator tenantIdGenerator,
            StoreIdGenerator storeIdGenerator,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new RegisterOrganizationHandler(
                organizationRepository,
                organizationIdGenerator,
                tenantIdGenerator,
                storeIdGenerator,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }

    @Bean
    ActivateOrganizationHandler activateOrganizationHandler(
            OrganizationRepository organizationRepository,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new ActivateOrganizationHandler(
                organizationRepository,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }

    @Bean
    AddStoreHandler addStoreHandler(
            OrganizationRepository organizationRepository,
            StoreIdGenerator storeIdGenerator,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new AddStoreHandler(
                organizationRepository,
                storeIdGenerator,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }

    @Bean
    ChangeHeadquartersHandler changeHeadquartersHandler(
            OrganizationRepository organizationRepository,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new ChangeHeadquartersHandler(
                organizationRepository,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }

    @Bean
    DeactivateStoreHandler deactivateStoreHandler(
            OrganizationRepository organizationRepository,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new DeactivateStoreHandler(
                organizationRepository,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }

    @Bean
    CloseOrganizationHandler closeOrganizationHandler(
            OrganizationRepository organizationRepository,
            EventIdGenerator eventIdGenerator,
            ClockProvider clockProvider,
            DomainEventPublisher domainEventPublisher
    ) {
        return new CloseOrganizationHandler(
                organizationRepository,
                eventIdGenerator,
                clockProvider,
                domainEventPublisher
        );
    }
}