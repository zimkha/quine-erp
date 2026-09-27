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
import com.zim.shared.domain.TenantId;
import com.zim.organization.infrastructure.event.SpringDomainEventPublisher;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

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
      OrganizationPersistenceMapper mapper,
      EntityManager entityManager
  ) {
    return new OrganizationRepositoryAdapter(
        springDataRepository,
        mapper,
        entityManager
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
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new RegisterOrganizationHandler(
            organizationRepository,
            organizationIdGenerator,
            tenantIdGenerator,
            storeIdGenerator,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  @Bean
  ActivateOrganizationHandler activateOrganizationHandler(
      OrganizationRepository organizationRepository,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new ActivateOrganizationHandler(
            organizationRepository,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  @Bean
  AddStoreHandler addStoreHandler(
      OrganizationRepository organizationRepository,
      StoreIdGenerator storeIdGenerator,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new AddStoreHandler(
            organizationRepository,
            storeIdGenerator,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  @Bean
  ChangeHeadquartersHandler changeHeadquartersHandler(
      OrganizationRepository organizationRepository,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new ChangeHeadquartersHandler(
            organizationRepository,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  @Bean
  DeactivateStoreHandler deactivateStoreHandler(
      OrganizationRepository organizationRepository,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new DeactivateStoreHandler(
            organizationRepository,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  @Bean
  CloseOrganizationHandler closeOrganizationHandler(
      OrganizationRepository organizationRepository,
      EventIdGenerator eventIdGenerator,
      ClockProvider clockProvider,
      DomainEventPublisher domainEventPublisher,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new CloseOrganizationHandler(
            organizationRepository,
            eventIdGenerator,
            clockProvider,
            domainEventPublisher
        ),
        transactionManager
    );
  }

  /**
   * Wraps a command handler so each {@code handle(..)} call runs in one
   * transaction: load, modify, save and event publication all commit or
   * roll back together. This keeps Spring out of the application layer.
   */
  private static <T> T transactional(
      T handler,
      PlatformTransactionManager transactionManager
  ) {
    NameMatchTransactionAttributeSource attributes =
        new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod(
        "handle",
        new DefaultTransactionAttribute()
    );

    ProxyFactory proxyFactory = new ProxyFactory(handler);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, attributes)
    );

    @SuppressWarnings("unchecked")
    T proxy = (T) proxyFactory.getProxy(
        handler.getClass().getClassLoader()
    );
    return proxy;
  }
}
