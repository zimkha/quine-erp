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
import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.domain.event.HeadquartersChanged;
import com.zim.organization.domain.event.OrganizationActivated;
import com.zim.organization.domain.event.OrganizationRegistered;
import com.zim.organization.domain.event.StoreAdded;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.DomainEvent;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
    OrganizationConfiguration.class,
    OrganizationConfigurationIT.CommittedEventCollector.class
})
class OrganizationConfigurationIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("quine")
          .withUsername("quine")
          .withPassword("quine");

  @DynamicPropertySource
  static void configurePostgres(
      DynamicPropertyRegistry registry
  ) {
    registry.add(
        "spring.datasource.url",
        POSTGRES::getJdbcUrl
    );

    registry.add(
        "spring.datasource.username",
        POSTGRES::getUsername
    );

    registry.add(
        "spring.datasource.password",
        POSTGRES::getPassword
    );
  }

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private OrganizationPersistenceMapper persistenceMapper;

  @Autowired
  private OrganizationIdGenerator organizationIdGenerator;

  @Autowired
  private TenantIdGenerator tenantIdGenerator;

  @Autowired
  private StoreIdGenerator storeIdGenerator;

  @Autowired
  private EventIdGenerator eventIdGenerator;

  @Autowired
  private ClockProvider clockProvider;

  @Autowired
  private DomainEventPublisher domainEventPublisher;

  @Autowired
  private RegisterOrganizationHandler registerOrganizationHandler;

  @Autowired
  private ActivateOrganizationHandler activateOrganizationHandler;

  @Autowired
  private AddStoreHandler addStoreHandler;

  @Autowired
  private ChangeHeadquartersHandler changeHeadquartersHandler;

  @Autowired
  private DeactivateStoreHandler deactivateStoreHandler;

  @Autowired
  private CloseOrganizationHandler closeOrganizationHandler;

  @Autowired
  private CommittedEventCollector committedEvents;

  @BeforeEach
  void clearCommittedEvents() {
    committedEvents.events.clear();
  }

  @Test
  void shouldLoadOrganizationBoundedContextBeans() {
    assertThat(organizationRepository)
        .isNotNull();

    assertThat(persistenceMapper)
        .isNotNull();

    assertThat(organizationIdGenerator)
        .isNotNull();

    assertThat(tenantIdGenerator)
        .isNotNull();

    assertThat(storeIdGenerator)
        .isNotNull();

    assertThat(eventIdGenerator)
        .isNotNull();

    assertThat(clockProvider)
        .isNotNull();

    assertThat(domainEventPublisher)
        .isNotNull();

    assertThat(registerOrganizationHandler)
        .isNotNull();

    assertThat(activateOrganizationHandler)
        .isNotNull();

    assertThat(addStoreHandler)
        .isNotNull();

    assertThat(changeHeadquartersHandler)
        .isNotNull();

    assertThat(deactivateStoreHandler)
        .isNotNull();

    assertThat(closeOrganizationHandler)
        .isNotNull();
  }
  @Test
  void shouldGenerateIdentifiers() {
    assertThat(organizationIdGenerator.generate())
        .isNotNull();

    assertThat(tenantIdGenerator.generate())
        .isNotNull();

    assertThat(storeIdGenerator.generate())
        .isNotNull();

    assertThat(eventIdGenerator.generate())
        .isNotNull();
  }
  @Test
  void shouldGenerateDifferentOrganizationIds() {
    assertThat(
        organizationIdGenerator.generate()
    ).isNotEqualTo(
        organizationIdGenerator.generate()
    );
  }

  @Test
  void shouldExposeHandlersAsTransactionalProxies() {
    assertThat(List.of(
        registerOrganizationHandler,
        activateOrganizationHandler,
        addStoreHandler,
        changeHeadquartersHandler,
        deactivateStoreHandler,
        closeOrganizationHandler
    )).allMatch(AopUtils::isAopProxy);
  }

  /**
   * Runs without a test-managed transaction, so each handler call must open
   * and commit its own. The adapter's version lock fails outside a
   * transaction, and AFTER_COMMIT listeners only fire if events were
   * published inside one.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void shouldRunEachHandlerInItsOwnCommittedTransaction() {
    RegisterOrganizationResult registered =
        registerOrganizationHandler.handle(
            new RegisterOrganizationCommand(
                "Quincaillerie Thiès",
                "Quincaillerie Thiès " + UUID.randomUUID(),
                "XOF",
                "THIES-01",
                "Magasin principal"
            )
        );
    UUID organizationId = registered.organizationId();
    UUID thiesId = registered.headquartersId();

    activateOrganizationHandler.handle(
        new ActivateOrganizationCommand(organizationId)
    );
    UUID dakarId = addStoreHandler.handle(
        new AddStoreCommand(organizationId, "DAKAR-01", "Magasin Dakar")
    ).storeId();

    changeHeadquartersHandler.handle(
        new ChangeHeadquartersCommand(organizationId, dakarId)
    );
    changeHeadquartersHandler.handle(
        new ChangeHeadquartersCommand(organizationId, thiesId)
    );

    Organization reloaded = organizationRepository
        .findById(new OrganizationId(organizationId))
        .orElseThrow();

    assertThat(reloaded.status()).isEqualTo(OrganizationStatus.ACTIVE);
    assertThat(reloaded.stores())
        .filteredOn(Store::isHeadquarters)
        .singleElement()
        .extracting(Store::id)
        .isEqualTo(new StoreId(thiesId));

    assertThat(committedEvents.events)
        .extracting(event -> (Class) event.getClass())
        .containsExactly(
            OrganizationRegistered.class,
            OrganizationActivated.class,
            StoreAdded.class,
            HeadquartersChanged.class,
            HeadquartersChanged.class
        );
  }

  static class CommittedEventCollector {

    final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(DomainEvent event) {
      events.add(event);
    }
  }
}
