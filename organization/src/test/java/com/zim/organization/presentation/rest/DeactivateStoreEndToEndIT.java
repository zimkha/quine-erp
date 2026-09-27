package com.zim.organization.presentation.rest;

import com.zim.organization.OrganizationJpaTestApplication;
import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.infrastructure.configuration.OrganizationConfiguration;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/organizations/{id}/stores/{storeId}/deactivation over the real
 * handler, repository adapter and Postgres. Only the tenant provider is
 * stubbed, until identity exists. Same setup as
 * {@link ChangeHeadquartersEndToEndIT}.
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
class DeactivateStoreEndToEndIT {

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
  private AddStoreHandler addStoreHandler;

  @Autowired
  private SettableTenantProvider tenantProvider;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private TenantId tenantA;
  private UUID organizationOfA;
  private UUID headquartersOfA;
  private UUID secondStoreOfA;

  /**
   * Tenant A's ACTIVE organization with its headquarters and a second,
   * active store S, seeded through the handlers.
   */
  @BeforeEach
  void seedActiveOrganizationOfTenantAWithTwoStores() {
    RegisterOrganizationResult registered = register();
    tenantA = new TenantId(registered.tenantId());
    organizationOfA = registered.organizationId();
    headquartersOfA = registered.headquartersId();

    activateOrganizationHandler.handle(
        new ActivateOrganizationCommand(tenantA, organizationOfA)
    );
    secondStoreOfA = addStoreHandler.handle(
        new AddStoreCommand(tenantA, organizationOfA, "THIES-02", "Magasin 2")
    ).storeId();
  }

  @AfterEach
  void resetTenantProvider() {
    tenantProvider.clear();
  }

  @Test
  void shouldDeactivateStoreUnderCallerTenant() throws Exception {
    tenantProvider.set(tenantA);

    deactivateStore(organizationOfA, secondStoreOfA)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId")
            .value(organizationOfA.toString()))
        .andExpect(jsonPath("$.storeId").value(secondStoreOfA.toString()))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.deactivatedAt").exists());

    assertThat(isActive(secondStoreOfA)).isFalse();
    assertThat(isActive(headquartersOfA)).isTrue();
    assertThat(headquartersIds(organizationOfA))
        .containsExactly(headquartersOfA);
  }

  /**
   * Tenant B sending A's organization id and A's real store id gets exactly
   * the 404 of a missing organization, and S is still active.
   */
  @Test
  void shouldAnswerNotFoundToAnotherTenant() throws Exception {
    TenantId tenantB = new TenantId(register().tenantId());
    tenantProvider.set(tenantB);
    UUID randomId = UUID.randomUUID();

    String crossTenantBody = deactivateStore(organizationOfA, secondStoreOfA)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    String missingBody = deactivateStore(randomId, secondStoreOfA)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
        .andReturn().getResponse().getContentAsString();

    // The message quotes the organization id from the caller's own path;
    // once that and the timestamp are set aside, the bodies are identical.
    assertThat(normalized(crossTenantBody, organizationOfA))
        .isEqualTo(normalized(missingBody, randomId));
    assertThat(isActive(secondStoreOfA)).isTrue();
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

  private ResultActions deactivateStore(UUID organizationId, UUID storeId)
      throws Exception {
    return mockMvc.perform(
        post(
            "/api/organizations/{id}/stores/{storeId}/deactivation",
            organizationId,
            storeId
        )
    );
  }

  private boolean isActive(UUID storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT active FROM organization.stores WHERE id = ?",
        Boolean.class,
        storeId
    );
  }

  private List<UUID> headquartersIds(UUID organizationId) {
    return jdbcTemplate.queryForList(
        "SELECT id FROM organization.stores "
            + "WHERE organization_id = ? AND headquarters",
        UUID.class,
        organizationId
    );
  }

  private static String normalized(String body, UUID pathId) {
    return body
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
        .replace(pathId.toString(), "{id}");
  }
}
