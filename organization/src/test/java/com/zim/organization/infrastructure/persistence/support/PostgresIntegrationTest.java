package com.zim.organization.infrastructure.persistence.support;

import com.zim.organization.OrganizationJpaTestApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for JPA integration tests against a real Postgres.
 *
 * <p>The container is a JVM-wide singleton (stopped by Testcontainers' Ryuk
 * at JVM exit) rather than a JUnit-managed {@code @Container}: Spring caches
 * the application context across test classes, so the database it migrated
 * must outlive any single test class.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
    FlywayAutoConfiguration.class
)
@Import(
    OrganizationJpaTestApplication.class
)
public abstract class PostgresIntegrationTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("quine")
          .withUsername("quine")
          .withPassword("quine");

  static {
    POSTGRES.start();
  }

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
}