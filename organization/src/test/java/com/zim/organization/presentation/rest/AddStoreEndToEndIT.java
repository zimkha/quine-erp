package com.zim.organization.presentation.rest;

import com.jayway.jsonpath.JsonPath;
import com.zim.organization.OrganizationJpaTestApplication;
import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.infrastructure.configuration.OrganizationConfiguration;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/organizations/{id}/stores over the real handler, repository
 * adapter and Postgres. Only the tenant provider is stubbed, until identity
 * exists.
 *
 * <p>Runs without a test-managed transaction, as in production: every
 * handler call commits its own. Each test registers its own tenants with a
 * unique legal name, so committed rows don't collide across runs.
 */
@SpringBootTest(classes = OrganizationJpaTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// The test applications do not component-scan: the web slice, the module
// wiring and the stub provider are imported explicitly.
@Import({
    OrganizationConfiguration.class,
    TenantOrganizationController.class,
    ApiExceptionHandler.class,
    AddStoreEndToEndIT.SettableTenantProviderConfiguration.class
})
class AddStoreEndToEndIT {

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> PostgresIntegrationTest.container().getJdbcUrl()
    );
    registry.add(
        "spring.datasource.username",
        () -> PostgresIntegrationTest.container().getUsername()
    );
    registry.add(
        "spring.datasource.password",
        () -> PostgresIntegrationTest.container().getPassword()
    );
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RegisterOrganizationHandler registerOrganizationHandler;

  @Autowired
  private ActivateOrganizationHandler activateOrganizationHandler;

  @Autowired
  private SettableTenantProvider tenantProvider;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private TenantId tenantA;
  private UUID organizationOfA;

  @BeforeEach
  void seedActiveOrganizationOfTenantA() {
    tenantProvider.clear();

    RegisterOrganizationResult registered = register();
    tenantA = new TenantId(registered.tenantId());
    organizationOfA = registered.organizationId();

    activateOrganizationHandler.handle(
        new ActivateOrganizationCommand(tenantA, organizationOfA)
    );
  }

  @Test
  void shouldAddStoreUnderCallerTenant() throws Exception {
    tenantProvider.set(tenantA);

    String body = addStore(organizationOfA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.organizationId")
            .value(organizationOfA.toString()))
        .andExpect(jsonPath("$.storeCode").value("THIES-02"))
        .andExpect(jsonPath("$.headquarters").value(false))
        .andExpect(jsonPath("$.active").value(true))
        .andReturn()
        .getResponse()
        .getContentAsString();

    UUID storedTenant = jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM organization.stores "
            + "WHERE id = ? AND organization_id = ? AND code = 'THIES-02'",
        UUID.class,
        UUID.fromString(JsonPath.read(body, "$.storeId")),
        organizationOfA
    );
    assertThat(storedTenant).isEqualTo(tenantA.value());
    assertThat(storeCount(organizationOfA)).isEqualTo(2);
  }

  /**
   * Tenant B posting to A's organization gets exactly the 404 of a missing
   * organization, and nothing is written.
   */
  @Test
  void shouldAnswerNotFoundToAnotherTenant() throws Exception {
    TenantId tenantB = new TenantId(register().tenantId());
    tenantProvider.set(tenantB);
    UUID randomId = UUID.randomUUID();

    String crossTenantBody = addStore(organizationOfA)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    String missingBody = addStore(randomId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    // The message quotes the organization id from the caller's own path;
    // once that and the timestamp are set aside, the bodies are identical.
    assertThat(normalized(crossTenantBody, organizationOfA))
        .isEqualTo(normalized(missingBody, randomId));
    assertThat(storeCount(organizationOfA)).isEqualTo(1);
  }

  private RegisterOrganizationResult register() {
    return registerOrganizationHandler.handle(
        new RegisterOrganizationCommand(
            "Quincaillerie Thiès",
            "Quincaillerie Thiès " + UUID.randomUUID(),
            "XOF",
            "THIES-01",
            "Magasin principal"
        )
    );
  }

  private ResultActions addStore(UUID organizationId) throws Exception {
    return mockMvc.perform(
        post("/api/organizations/{id}/stores", organizationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"storeCode": "thies-02", "storeName": "Magasin 2"}
                """)
    );
  }

  private int storeCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM organization.stores WHERE organization_id = ?",
        Integer.class,
        organizationId
    );
  }

  private static String normalized(String body, UUID pathId) {
    return body
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
        .replace(pathId.toString(), "{id}");
  }

  /**
   * Stands in for the identity-backed provider. Register generates the
   * tenant, so each test sets the tenant it acts for; unset, it fails
   * closed like any real provider.
   */
  static class SettableTenantProvider implements CurrentTenantProvider {

    private final AtomicReference<TenantId> tenant = new AtomicReference<>();

    void set(TenantId tenantId) {
      tenant.set(tenantId);
    }

    void clear() {
      tenant.set(null);
    }

    @Override
    public TenantId currentTenant() {
      TenantId current = tenant.get();
      if (current == null) {
        throw new TenantNotResolvedException();
      }
      return current;
    }
  }

  @TestConfiguration
  static class SettableTenantProviderConfiguration {

    @Bean
    SettableTenantProvider currentTenantProvider() {
      return new SettableTenantProvider();
    }
  }
}
