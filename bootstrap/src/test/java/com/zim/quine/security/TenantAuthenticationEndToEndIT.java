package com.zim.quine.security;

import com.jayway.jsonpath.JsonPath;
import com.zim.quine.PostgresContainerSupport;
import com.zim.quine.QuineApplication;
import com.zim.quine.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T7b-AC8 and T7b-AC11 on a real Postgres and the full wiring, with the real
 * chain: register an owner, log in, activate one's own organization through
 * the tenant route (the flag is on here and in docker-compose only), act with
 * the token, and never reach another tenant's organization.
 */
@SpringBootTest(
    classes = QuineApplication.class,
    properties = "quine.organization.tenant-activation.enabled=true"
)
@AutoConfigureMockMvc
class TenantAuthenticationEndToEndIT {

  // Fictitious test value of the required length.
  private static final String PASSWORD = "not-a-real-password-1";

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
    registry.add("quine.security.tenant.jwt.secret", TestJwtSecret::value);
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbcTemplate;

  /** A registered tenant: its organization, tenant and a token from a real login. */
  private record Tenant(UUID organizationId, UUID tenantId, UUID headquartersId, String token) {
  }

  @Test
  void shouldActivateOwnOrganizationThenActForTheTenantOfTheToken() throws Exception {
    Tenant a = registerAndLogin();

    MvcResult activation = mockMvc.perform(authorized(
        post("/api/organizations/{id}/activation", a.organizationId()), a.token())).andReturn();
    assertThat(activation.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(activation.getResponse().getContentAsString(), "$.status"))
        .isEqualTo("ACTIVE");
    assertThat(statusOf(a.organizationId())).isEqualTo("ACTIVE");

    MvcResult store = mockMvc.perform(authorized(
            post("/api/organizations/{id}/stores", a.organizationId()), a.token())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeCode\": \"thies-02\", \"storeName\": \"Magasin 2\"}")).andReturn();
    assertThat(store.getResponse().getStatus()).isEqualTo(201);

    UUID storeId = UUID.fromString(JsonPath.read(store.getResponse().getContentAsString(), "$.storeId"));
    // The command's tenant is the token's: the stored store belongs to it.
    assertThat(jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM organization.stores WHERE id = ?", UUID.class, storeId))
        .isEqualTo(a.tenantId());
    assertThat(jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM organization.organizations WHERE id = ?", UUID.class, a.organizationId()))
        .isEqualTo(a.tenantId());
  }

  @Test
  void shouldNotLetOneTenantActivateWithoutItsToken() throws Exception {
    Tenant a = registerAndLogin();

    MvcResult result = mockMvc.perform(
        post("/api/organizations/{id}/activation", a.organizationId())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(statusOf(a.organizationId())).isEqualTo("PENDING_ACTIVATION");
  }

  /** T7b-AC11: every tenant endpoint answers a foreign organization like a missing one. */
  @Test
  void shouldAnswerTheOwnerOfAOtherTenantsOrganizationLikeAMissingOne() throws Exception {
    Tenant a = registerAndLogin();
    Tenant b = registerAndLogin();
    UUID missing = UUID.randomUUID();

    List<Function<UUID, MockHttpServletRequestBuilder>> endpoints = List.of(
        id -> post("/api/organizations/{id}/stores", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"storeCode\": \"thies-02\", \"storeName\": \"Magasin 2\"}"),
        id -> put("/api/organizations/{id}/headquarters", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"storeId\": \"" + UUID.randomUUID() + "\"}"),
        id -> post("/api/organizations/{id}/stores/{storeId}/deactivation", id, UUID.randomUUID()),
        id -> post("/api/organizations/{id}/closure", id),
        id -> post("/api/organizations/{id}/activation", id)
    );

    for (Function<UUID, MockHttpServletRequestBuilder> endpoint : endpoints) {
      MvcResult foreign = mockMvc.perform(authorized(endpoint.apply(b.organizationId()), a.token())).andReturn();
      MvcResult absent = mockMvc.perform(authorized(endpoint.apply(missing), a.token())).andReturn();

      assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
      assertThat(absent.getResponse().getStatus()).isEqualTo(404);
      String foreignBody = foreign.getResponse().getContentAsString();
      String absentBody = absent.getResponse().getContentAsString();
      assertThat((String) JsonPath.read(foreignBody, "$.code")).isEqualTo("ORGANIZATION_NOT_FOUND");
      assertThat((String) JsonPath.read(foreignBody, "$.code"))
          .isEqualTo(JsonPath.read(absentBody, "$.code"));
      // The message names the requested id and nothing else.
      assertThat(((String) JsonPath.read(foreignBody, "$.message")).replace(b.organizationId().toString(), "<id>"))
          .isEqualTo(((String) JsonPath.read(absentBody, "$.message")).replace(missing.toString(), "<id>"));
    }

    // Nothing of B's was touched.
    assertThat(statusOf(b.organizationId())).isEqualTo("PENDING_ACTIVATION");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM organization.stores WHERE organization_id = ?",
        Long.class, b.organizationId())).isEqualTo(1L);
  }

  private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private String statusOf(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM organization.organizations WHERE id = ?", String.class, organizationId);
  }

  private Tenant registerAndLogin() throws Exception {
    String email = "owner-" + UUID.randomUUID() + "@example.com";
    String registration = """
        {"organizationName": "Quincaillerie Thiès",
         "legalName": "Quincaillerie %s",
         "currencyCode": "XOF",
         "headquartersCode": "THIES-01",
         "headquartersName": "Magasin principal",
         "ownerEmail": "%s",
         "ownerPassword": "%s"}
        """.formatted(UUID.randomUUID(), email, PASSWORD);
    MvcResult registered = mockMvc.perform(post("/api/organizations")
        .contentType(MediaType.APPLICATION_JSON).content(registration)).andReturn();
    assertThat(registered.getResponse().getStatus()).isEqualTo(201);
    String body = registered.getResponse().getContentAsString();

    MvcResult login = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))).andReturn();
    assertThat(login.getResponse().getStatus()).isEqualTo(200);

    return new Tenant(
        UUID.fromString(JsonPath.read(body, "$.organizationId")),
        UUID.fromString(JsonPath.read(body, "$.tenantId")),
        UUID.fromString(JsonPath.read(body, "$.headquartersId")),
        JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken")
    );
  }
}
