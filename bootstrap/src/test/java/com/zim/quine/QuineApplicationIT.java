package com.zim.quine;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.handler.CreateOwnerHandler;
import com.zim.identity.application.result.CreateOwnerResult;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Autowired
  PlatformTransactionManager transactionManager;

  /**
   * Registration (T7c) will call the handler inside its own transaction, so a
   * failed owner creation must undo what that transaction wrote before it.
   */
  @Test
  void shouldRollBackTheCallersWritesWhenOwnerCreationFails() {
    String taken = unique("taken");
    createOwnerHandler.handle(
        new CreateOwnerCommand(TenantId.generate(), taken, PASSWORD)
    );
    UUID markerId = UUID.randomUUID();

    assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          jdbcTemplate.update(
              "INSERT INTO identity.users "
                  + "(id, tenant_id, email, password_hash, owner, created_at) "
                  + "VALUES (?, ?, ?, 'marker', false, now())",
              markerId, UUID.randomUUID(), unique("marker")
          );
          createOwnerHandler.handle(
              new CreateOwnerCommand(TenantId.generate(), taken, PASSWORD)
          );
        }))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("EMAIL_ALREADY_EXISTS");

    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM identity.users WHERE id = ?",
        Integer.class,
        markerId
    )).isZero();
  }

  /** Two owners for one tenant: the database lets exactly one in. */
  @Test
  void shouldAllowOnlyOneOwnerPerTenantUnderConcurrency() throws Exception {
    for (int round = 0; round < 15; round++) {
      TenantId tenant = TenantId.generate();

      List<Object> outcomes = runConcurrently(
          () -> createOwnerHandler.handle(
              new CreateOwnerCommand(tenant, unique("a"), PASSWORD)),
          () -> createOwnerHandler.handle(
              new CreateOwnerCommand(tenant, unique("b"), PASSWORD))
      );

      assertThat(codesOfFailures(outcomes))
          .containsExactly("TENANT_ALREADY_HAS_OWNER");
      assertThat(outcomes).filteredOn(CreateOwnerResult.class::isInstance)
          .hasSize(1);
    }
  }

  /** Two tenants registering the same e-mail: exactly one wins. */
  @Test
  void shouldAllowOnlyOneUserPerEmailUnderConcurrency() throws Exception {
    for (int round = 0; round < 15; round++) {
      String email = unique("race");

      List<Object> outcomes = runConcurrently(
          () -> createOwnerHandler.handle(
              new CreateOwnerCommand(TenantId.generate(), email, PASSWORD)),
          () -> createOwnerHandler.handle(
              new CreateOwnerCommand(TenantId.generate(), email, PASSWORD))
      );

      assertThat(codesOfFailures(outcomes))
          .containsExactly("EMAIL_ALREADY_EXISTS");
      assertThat(outcomes).filteredOn(CreateOwnerResult.class::isInstance)
          .hasSize(1);
    }
  }

  /** Releases both calls together; returns each result or its exception. */
  private static List<Object> runConcurrently(
      Callable<CreateOwnerResult> first,
      Callable<CreateOwnerResult> second
  ) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (Callable<CreateOwnerResult> call : List.of(first, second)) {
        futures.add(executor.submit(() -> {
          start.await();
          try {
            return (Object) call.call();
          } catch (Exception exception) {
            return exception;
          }
        }));
      }
      start.countDown();
      List<Object> outcomes = new ArrayList<>();
      for (Future<Object> future : futures) {
        outcomes.add(future.get());
      }
      return outcomes;
    } finally {
      executor.shutdownNow();
    }
  }

  private static List<String> codesOfFailures(List<Object> outcomes) {
    return outcomes.stream()
        .filter(Exception.class::isInstance)
        .map(outcome -> ((BusinessRuleViolationException) outcome).code())
        .toList();
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }

  private static final String PASSWORD = "S3cret-Passw0rd!";

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
