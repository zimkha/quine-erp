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
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(OrganizationConfiguration.class)
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
}
