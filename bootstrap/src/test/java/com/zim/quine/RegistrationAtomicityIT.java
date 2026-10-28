package com.zim.quine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Registration end to end against a real Postgres, through the real
 * wiring of both modules: the owner is created with the organization, and a
 * failed owner creation leaves no organization behind.
 */
@SpringBootTest(classes = QuineApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class RegistrationAtomicityIT {

  // Obviously fake, and built so no literal secret sits in the source.
  private static final String FAKE_PASSWORD = "placeholder-" + "owner-value";

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateTheOrganizationAndItsOwnerTogether() throws Exception {
    String email = "Owner-" + UUID.randomUUID() + "@Example.com";
    String legalName = "Legal " + UUID.randomUUID();

    String body = register(legalName, email)
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String tenantId = com.jayway.jsonpath.JsonPath.read(body, "$.tenantId");
    assertThat(body).doesNotContain(FAKE_PASSWORD).doesNotContain("$2");
    assertThat(organizationsNamed(legalName)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM identity.users "
            + "WHERE tenant_id = ?::uuid AND owner AND email = ?",
        Integer.class, tenantId, email.toLowerCase()
    )).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT password_hash FROM identity.users WHERE tenant_id = ?::uuid",
        String.class, tenantId
    )).startsWith("$2").doesNotContain(FAKE_PASSWORD);
  }

  @Test
  void shouldRollBackTheOrganizationWhenTheEmailIsAlreadyTaken()
      throws Exception {
    String email = "taken-" + UUID.randomUUID() + "@example.com";
    register("Legal " + UUID.randomUUID(), email)
        .andExpect(status().isCreated());
    int organizationsBefore = organizationCount();
    int usersBefore = userCount();
    String secondLegalName = "Legal " + UUID.randomUUID();

    String body = register(secondLegalName, email.toUpperCase())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(FAKE_PASSWORD);
    assertThat(organizationsNamed(secondLegalName)).isZero();
    assertThat(organizationCount()).isEqualTo(organizationsBefore);
    assertThat(userCount()).isEqualTo(usersBefore);
  }

  @Test
  void shouldRollBackTheOrganizationWhenTheDomainRejectsTheEmail()
      throws Exception {
    String legalName = "Legal " + UUID.randomUUID();
    int usersBefore = userCount();

    // Passes the request's @Email but not identity's stricter structure.
    String body = register(legalName, "someone@localhost")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(FAKE_PASSWORD);
    assertThat(organizationsNamed(legalName)).isZero();
    assertThat(userCount()).isEqualTo(usersBefore);
  }

  private ResultActions register(String legalName, String ownerEmail)
      throws Exception {
    return mockMvc.perform(
        post("/api/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                 "organizationName": "Quincaillerie",
                 "legalName": "%s",
                 "currencyCode": "XOF",
                 "headquartersCode": "THIES-01",
                 "headquartersName": "Magasin principal",
                 "ownerEmail": "%s",
                 "ownerPassword": "%s"
                }
                """.formatted(legalName, ownerEmail, FAKE_PASSWORD))
    );
  }

  private int organizationsNamed(String legalName) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM organization.organizations WHERE legal_name = ?",
        Integer.class, legalName
    );
  }

  private int organizationCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM organization.organizations", Integer.class);
  }

  private int userCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM identity.users", Integer.class);
  }
}
