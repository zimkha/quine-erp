package com.zim.quine;

import com.zim.identity.infrastructure.security.JwtTenantProvider;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7b-AC14: with no profile and a key the app starts, the only tenant
 * provider is the JWT one, the stand-ins and the smoke shortcut are gone
 * (default deny answers 401), and the tenant activation route, off by
 * default, does not exist (no flag is set here).
 */
@SpringBootTest(classes = QuineApplication.class)
@AutoConfigureMockMvc
class QuineApplicationWithoutProfileIT {

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
    registry.add("quine.security.tenant.jwt.secret", TestJwtSecret::value);
  }

  @Autowired
  ApplicationContext context;

  @Autowired
  Environment environment;

  @Autowired
  MockMvc mockMvc;

  /**
   * With open-in-view a Hibernate session bound to the request could keep its
   * connection while BCrypt runs in the login. Nothing else pins this setting.
   */
  @Test
  void shouldNotKeepAHibernateSessionOpenForTheWholeRequest() {
    assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
  }

  @Test
  void shouldStartWithNoProfileAndAKey() {
    assertThat(environment.getActiveProfiles()).isEmpty();
    assertThat(environment.getProperty("quine.organization.tenant-activation.enabled")).isNull();
  }

  @Test
  void shouldHaveExactlyOneTenantProviderAndItIsTheJwtOne() {
    assertThat(context.getBeansOfType(CurrentTenantProvider.class).values())
        .singleElement()
        .isInstanceOf(JwtTenantProvider.class);
  }

  @Test
  void shouldAnswer401ToTheRemovedSmokeShortcutWhateverTheHeader() throws Exception {
    mockMvc.perform(post("/smoke/organizations/{id}/activate", java.util.UUID.randomUUID())
            .header("X-Smoke-Tenant", java.util.UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"quine-erp\""))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"));
    mockMvc.perform(get("/smoke/x"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldNotHaveTheTenantActivationRouteWithoutTheFlag() throws Exception {
    java.util.UUID id = java.util.UUID.randomUUID();

    mockMvc.perform(post("/api/organizations/{id}/activation", id))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/organizations/{id}/activation", id)
            .header("Authorization", "Bearer " + TestTokens.valid(java.util.UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }
}
