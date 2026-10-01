package com.zim.organization.presentation.rest;

import com.zim.organization.OrganizationJpaTestApplication;
import com.zim.organization.application.command.CloseOrganizationCommand;
import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.handler.CloseOrganizationHandler;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.infrastructure.configuration.OrganizationConfiguration;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/organizations/{id}/activation over the real handler, repository
 * adapter and Postgres. Only the tenant provider is stubbed, until identity
 * exists. Same setup as {@link DeactivateStoreEndToEndIT}.
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
    SettableTenantProvider.ProviderConfiguration.class
})
class ActivateOrganizationEndToEndIT {

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
  private CloseOrganizationHandler closeOrganizationHandler;

  @Autowired
  private SettableTenantProvider tenantProvider;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @AfterEach
  void resetTenantProvider() {
    tenantProvider.clear();
  }

  /**
   * Register, activate, then add a store, all over the real handlers and
   * Postgres: add store answers its "must be active" 409 before activation
   * and succeeds after it.
   */
  @Test
  void shouldActivateOrganizationThenAllowAddingStore() throws Exception {
    RegisterOrganizationResult registered = register();
    TenantId tenant = new TenantId(registered.tenantId());
    UUID organizationId = registered.organizationId();
    tenantProvider.set(tenant);

    addStore(organizationId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE"));
    assertThat(statusOf(organizationId)).isEqualTo("PENDING_ACTIVATION");

    activateOrganization(organizationId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId")
            .value(organizationId.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.activatedAt").exists())
        .andExpect(jsonPath("$.tenantId").doesNotExist());
    assertThat(statusOf(organizationId)).isEqualTo("ACTIVE");

    addStore(organizationId).andExpect(status().isCreated());
  }

  @Test
  void shouldAnswerRepeatedActivationWithAlreadyActive() throws Exception {
    RegisterOrganizationResult registered = register();
    tenantProvider.set(new TenantId(registered.tenantId()));
    UUID organizationId = registered.organizationId();

    activateOrganization(organizationId).andExpect(status().isOk());
    activateOrganization(organizationId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_ALREADY_ACTIVE"));

    assertThat(statusOf(organizationId)).isEqualTo("ACTIVE");
  }

  /**
   * A SUSPENDED organization is not reactivated through this endpoint
   * (that is reinstate()), and a CLOSED one never is. The status stays as it
   * was in Postgres. No handler suspends, so SUSPENDED is set in the row.
   */
  @Test
  void shouldRefuseToActivateSuspendedOrganization() throws Exception {
    RegisterOrganizationResult registered = register();
    tenantProvider.set(new TenantId(registered.tenantId()));
    UUID organizationId = registered.organizationId();
    jdbcTemplate.update(
        "UPDATE organization.organizations SET status = 'SUSPENDED' "
            + "WHERE id = ?",
        organizationId
    );

    activateOrganization(organizationId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_CANNOT_BE_ACTIVATED"));

    assertThat(statusOf(organizationId)).isEqualTo("SUSPENDED");
  }

  @Test
  void shouldRefuseToActivateClosedOrganization() throws Exception {
    RegisterOrganizationResult registered = register();
    TenantId tenant = new TenantId(registered.tenantId());
    UUID organizationId = registered.organizationId();
    tenantProvider.set(tenant);
    activateOrganization(organizationId).andExpect(status().isOk());
    closeOrganizationHandler.handle(
        new CloseOrganizationCommand(tenant, organizationId)
    );

    activateOrganization(organizationId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code")
            .value("ORGANIZATION_CANNOT_BE_ACTIVATED"));

    assertThat(statusOf(organizationId)).isEqualTo("CLOSED");
  }

  /**
   * Tenant B sending A's organization id gets exactly the 404 of a missing
   * organization, and A is still PENDING_ACTIVATION.
   */
  @Test
  void shouldAnswerNotFoundToAnotherTenant() throws Exception {
    UUID organizationOfA = register().organizationId();
    TenantId tenantB = new TenantId(register().tenantId());
    tenantProvider.set(tenantB);
    UUID randomId = UUID.randomUUID();

    String crossTenantBody = activateOrganization(organizationOfA)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    String missingBody = activateOrganization(randomId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    // The message quotes the organization id from the caller's own path;
    // once that and the timestamp are set aside, the bodies are identical.
    assertThat(normalized(crossTenantBody, organizationOfA))
        .isEqualTo(normalized(missingBody, randomId));
    assertThat(statusOf(organizationOfA)).isEqualTo("PENDING_ACTIVATION");
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

  private ResultActions activateOrganization(UUID organizationId)
      throws Exception {
    return mockMvc.perform(
        post("/api/organizations/{id}/activation", organizationId)
    );
  }

  private ResultActions addStore(UUID organizationId) throws Exception {
    return mockMvc.perform(
        post("/api/organizations/{id}/stores", organizationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"storeCode": "THIES-02", "storeName": "Magasin 2"}
                """)
    );
  }

  private String statusOf(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM organization.organizations WHERE id = ?",
        String.class,
        organizationId
    );
  }

  private static String normalized(String body, UUID pathId) {
    return body
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
        .replace(pathId.toString(), "{id}");
  }
}
