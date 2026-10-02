package com.zim.quine;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.handler.CreateOwnerHandler;
import com.zim.identity.application.result.CreateOwnerResult;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole application against a real Postgres: both modules migrate their
 * own schema, Hibernate validates both sets of entities, and the identity
 * handler works through the real wiring.
 */
@SpringBootTest(classes = QuineApplication.class)
@ActiveProfiles("smoke")
class QuineApplicationIT {

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
  }

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  CreateOwnerHandler createOwnerHandler;

  @Test
  void shouldGiveEachModuleItsOwnSchemaAndMigrationHistory() {
    assertThat(tablesIn("organization"))
        .contains("organizations", "stores", "flyway_schema_history");
    assertThat(tablesIn("identity"))
        .contains("users", "flyway_schema_history");
  }

  @Test
  void shouldCreateAnOwnerThroughTheRealWiring() {
    TenantId tenant = TenantId.generate();
    String email = "owner-" + UUID.randomUUID() + "@example.com";

    CreateOwnerResult result = createOwnerHandler.handle(
        new CreateOwnerCommand(tenant, email, "S3cret-Passw0rd!")
    );

    String storedHash = jdbcTemplate.queryForObject(
        "SELECT password_hash FROM identity.users WHERE id = ?",
        String.class,
        result.userId()
    );
    assertThat(storedHash).startsWith("$2").doesNotContain("S3cret");
  }

  private List<String> tablesIn(String schema) {
    return jdbcTemplate.queryForList(
        "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = ?",
        String.class,
        schema
    );
  }

  @Autowired
  CurrentTenantProvider tenantProvider;

  @Test
  void shouldUseTheSmokeProviderUnderTheSmokeProfile() {
    assertThat(tenantProvider.getClass().getSimpleName())
        .isEqualTo("SmokeTenantProvider");
  }
}
