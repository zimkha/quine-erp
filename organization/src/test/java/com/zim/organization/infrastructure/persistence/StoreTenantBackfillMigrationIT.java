package com.zim.organization.infrastructure.persistence;

import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies V3 to a database that stopped at V2 and holds data.
 *
 * <p>Deliberately Spring-free: the shared {@code @DataJpaTest} context has
 * already migrated its database to the latest version, and the migrations
 * hard-code the {@code organization} schema. Each test therefore creates its
 * own database on the shared container and drives Flyway directly.
 */
class StoreTenantBackfillMigrationIT {

  private static final String LOCATION =
      "classpath:db/migration/organization";

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-01T10:00:00Z");

  private final PostgreSQLContainer postgres =
      PostgresIntegrationTest.container();

  private String databaseName;

  private String url;

  @BeforeEach
  void createDatabase() throws SQLException {
    databaseName = "organization_v3_"
        + UUID.randomUUID().toString().replace("-", "");

    try (Connection connection = DriverManager.getConnection(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
    );
         Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + databaseName);
    }

    url = "jdbc:postgresql://" + postgres.getHost() + ":"
        + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        + "/" + databaseName;
  }

  @AfterEach
  void dropDatabase() throws SQLException {
    try (Connection connection = DriverManager.getConnection(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
    );
         Statement statement = connection.createStatement()) {
      statement.execute(
          "DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)"
      );
    }
  }

  @Test
  void shouldBackfillEveryStoreWithItsOrganizationTenant() throws SQLException {
    // Given: a database at V2
    migrateToV2();

    UUID tenantA = UUID.randomUUID();
    UUID organizationA = UUID.randomUUID();
    UUID storeA1 = UUID.randomUUID();
    UUID storeA2 = UUID.randomUUID();

    UUID tenantB = UUID.randomUUID();
    UUID organizationB = UUID.randomUUID();
    UUID storeB1 = UUID.randomUUID();
    UUID storeB2 = UUID.randomUUID();
    UUID storeB3 = UUID.randomUUID();

    try (Connection connection = connect()) {
      insertOrganization(connection, organizationA, tenantA, "Thiès SARL");
      insertStoreAtV2(connection, storeA1, organizationA, "THIES-01", true);
      insertStoreAtV2(connection, storeA2, organizationA, "THIES-02", false);

      insertOrganization(connection, organizationB, tenantB, "Dakar SARL");
      insertStoreAtV2(connection, storeB1, organizationB, "DAKAR-01", true);
      insertStoreAtV2(connection, storeB2, organizationB, "DAKAR-02", false);
      insertStoreAtV2(connection, storeB3, organizationB, "DAKAR-03", false);
    }

    // When
    MigrateResult result = migrateToLatest();

    // Then
    assertThat(result.initialSchemaVersion).isEqualTo("2");
    assertThat(result.targetSchemaVersion).isEqualTo("3");
    assertThat(result.migrationsExecuted).isEqualTo(1);

    try (Connection connection = connect()) {
      assertThat(storeTenants(connection))
          .containsExactlyInAnyOrderEntriesOf(Map.of(
              storeA1, tenantA,
              storeA2, tenantA,
              storeB1, tenantB,
              storeB2, tenantB,
              storeB3, tenantB
          ));

      assertThat(storesWithTenantDifferentFromOrganization(connection))
          .isZero();

      assertThat(tenantIdIsNullable(connection)).isFalse();
    }
  }

  @Test
  void shouldApplyToOrganizationWithoutStores() throws SQLException {
    // Given
    migrateToV2();

    UUID organization = UUID.randomUUID();

    try (Connection connection = connect()) {
      insertOrganization(
          connection,
          organization,
          UUID.randomUUID(),
          "Sans magasin SARL"
      );
    }

    // When
    MigrateResult result = migrateToLatest();

    // Then
    assertThat(result.success).isTrue();
    assertThat(result.initialSchemaVersion).isEqualTo("2");
    assertThat(result.targetSchemaVersion).isEqualTo("3");

    try (Connection connection = connect()) {
      assertThat(storeTenants(connection)).isEmpty();
      assertThat(tenantIdIsNullable(connection)).isFalse();
    }
  }

  @Test
  void shouldApplyToEmptyDatabase() throws SQLException {
    // Given
    migrateToV2();

    // When
    MigrateResult result = migrateToLatest();

    // Then
    assertThat(result.success).isTrue();
    assertThat(result.initialSchemaVersion).isEqualTo("2");
    assertThat(result.targetSchemaVersion).isEqualTo("3");

    try (Connection connection = connect()) {
      assertThat(tenantIdIsNullable(connection)).isFalse();
    }
  }

  private void migrateToV2() throws SQLException {
    MigrateResult result = Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .locations(LOCATION)
        .schemas("organization")
        .target("2")
        .load()
        .migrate();

    assertThat(result.targetSchemaVersion).isEqualTo("2");
    assertThat(result.migrationsExecuted).isEqualTo(2);

    // The column does not exist yet, so the seed below is really V2 data.
    try (Connection connection = connect()) {
      assertThat(tenantIdIsNullable(connection)).isNull();
    }
  }

  private MigrateResult migrateToLatest() {
    return Flyway.configure()
        .dataSource(url, postgres.getUsername(), postgres.getPassword())
        .locations(LOCATION)
        .schemas("organization")
        .load()
        .migrate();
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(
        url,
        postgres.getUsername(),
        postgres.getPassword()
    );
  }

  private static void insertOrganization(
      Connection connection,
      UUID id,
      UUID tenantId,
      String legalName
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO organization.organizations (
          id, tenant_id, name, legal_name, normalized_legal_name,
          currency, status, created_at
        )
        VALUES (?, ?, ?, ?, ?, 'XOF', 'ACTIVE', ?)
        """)) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setString(3, legalName);
      statement.setString(4, legalName);
      statement.setString(5, legalName.toUpperCase());
      statement.setTimestamp(6, Timestamp.from(CREATED_AT));
      statement.executeUpdate();
    }
  }

  private static void insertStoreAtV2(
      Connection connection,
      UUID id,
      UUID organizationId,
      String code,
      boolean headquarters
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO organization.stores (
          id, organization_id, code, name, headquarters, active, created_at
        )
        VALUES (?, ?, ?, ?, ?, TRUE, ?)
        """)) {
      statement.setObject(1, id);
      statement.setObject(2, organizationId);
      statement.setString(3, code);
      statement.setString(4, "Magasin " + code);
      statement.setBoolean(5, headquarters);
      statement.setTimestamp(6, Timestamp.from(CREATED_AT));
      statement.executeUpdate();
    }
  }

  private static Map<UUID, UUID> storeTenants(
      Connection connection
  ) throws SQLException {
    Map<UUID, UUID> tenants = new HashMap<>();

    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery(
             "SELECT id, tenant_id FROM organization.stores"
         )) {
      while (rows.next()) {
        tenants.put(
            rows.getObject("id", UUID.class),
            rows.getObject("tenant_id", UUID.class)
        );
      }
    }

    return tenants;
  }

  private static int storesWithTenantDifferentFromOrganization(
      Connection connection
  ) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("""
             SELECT count(*)
             FROM organization.stores s
             JOIN organization.organizations o ON o.id = s.organization_id
             WHERE s.tenant_id IS DISTINCT FROM o.tenant_id
             """)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  /**
   * @return whether stores.tenant_id accepts NULL, or {@code null} if the
   *     column does not exist
   */
  private static Boolean tenantIdIsNullable(
      Connection connection
  ) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("""
             SELECT is_nullable
             FROM information_schema.columns
             WHERE table_schema = 'organization'
               AND table_name = 'stores'
               AND column_name = 'tenant_id'
             """)) {
      return rows.next() ? "YES".equals(rows.getString(1)) : null;
    }
  }
}
