package com.zim.quine.registration;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.quine.PostgresContainerSupport;
import com.zim.quine.QuineApplication;
import com.zim.quine.TestJwtSecret;
import com.zim.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7c on a real Postgres and the real wiring of both modules: registration
 * creates the organization and its owner in one transaction, and a failing
 * owner leaves nothing behind. No profile is set: registration is public.
 * No test-managed transaction, so what is asserted is what really committed.
 */
@SpringBootTest(classes = QuineApplication.class)
@AutoConfigureMockMvc
class RegistrationCreatesOwnerIT {

  // Fictitious test values of the required length.
  private static final String PASSWORD = "not-a-real-password-1";
  private static final String OTHER_PASSWORD = "another-fake-password-2";

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
    registry.add("quine.security.tenant.jwt.secret", TestJwtSecret::value);
  }

  @Autowired
  MockMvc mockMvc;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  RegisterOrganizationHandler registerOrganizationHandler;

  @Test
  void shouldRegisterTheOrganizationAndItsOwnerTogether() throws Exception {
    String legalName = uniqueLegalName();
    String email = uniqueEmail();

    String response = register(legalName, email, PASSWORD)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tenantId").exists())
        .andReturn().getResponse().getContentAsString();

    assertThat(response)
        .doesNotContain(PASSWORD)
        .doesNotContainIgnoringCase("password")
        .doesNotContainIgnoringCase("hash")
        .doesNotContainIgnoringCase("token")
        .doesNotContain(email);

    UUID tenantId = UUID.fromString(
        com.jayway.jsonpath.JsonPath.read(response, "$.tenantId")
    );
    assertThat(count(
        "SELECT count(*) FROM organization.organizations WHERE tenant_id = ?",
        tenantId
    )).isEqualTo(1);

    List<Map<String, Object>> users = jdbcTemplate.queryForList(
        "SELECT email, owner, password_hash FROM identity.users "
            + "WHERE tenant_id = ?",
        tenantId
    );
    assertThat(users).singleElement().satisfies(user -> {
      assertThat(user.get("email")).isEqualTo(email.toLowerCase(Locale.ROOT));
      assertThat(user.get("owner")).isEqualTo(true);
      String hash = (String) user.get("password_hash");
      assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    });
  }

  @Test
  void shouldRollBackTheOrganizationWhenTheEmailIsTakenWhateverItsCase()
      throws Exception {
    String taken = uniqueEmail();
    register(uniqueLegalName(), taken, PASSWORD).andExpect(status().isCreated());

    String legalName = uniqueLegalName();
    long organizationsBefore = count("SELECT count(*) FROM organization.organizations");
    long usersBefore = count("SELECT count(*) FROM identity.users");
    long storesBefore = count("SELECT count(*) FROM organization.stores");

    String response = register(legalName, taken.toUpperCase(Locale.ROOT), OTHER_PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain(OTHER_PASSWORD).doesNotContain(PASSWORD);
    assertNothingCreated(legalName, organizationsBefore, usersBefore, storesBefore);
  }

  /** The handler normalizes (trim, lower-case) before checking uniqueness. */
  @Test
  void shouldRollBackWhenTheEmailIsTakenAfterTrimmingAndLowerCasing() {
    String taken = uniqueEmail();
    registerOrganizationHandler.handle(command(uniqueLegalName(), taken, PASSWORD));

    String legalName = uniqueLegalName();
    long organizationsBefore = count("SELECT count(*) FROM organization.organizations");
    long usersBefore = count("SELECT count(*) FROM identity.users");
    long storesBefore = count("SELECT count(*) FROM organization.stores");

    assertThatThrownBy(() -> registerOrganizationHandler.handle(
        command(legalName, "  " + taken.toUpperCase(Locale.ROOT) + " ", OTHER_PASSWORD)
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code").isEqualTo("EMAIL_ALREADY_EXISTS");

    assertNothingCreated(legalName, organizationsBefore, usersBefore, storesBefore);
  }

  /**
   * Identity refuses a password the request validation lets through (40
   * two-byte characters pass {@code @Size(max=72)} but are 80 bytes, over
   * bcrypt's limit): 422, and nothing is created either.
   */
  @Test
  void shouldRollBackWhenIdentityRejectsThePassword() throws Exception {
    String legalName = uniqueLegalName();
    long organizationsBefore = count("SELECT count(*) FROM organization.organizations");
    long usersBefore = count("SELECT count(*) FROM identity.users");
    long storesBefore = count("SELECT count(*) FROM organization.stores");

    register(legalName, uniqueEmail(), "é".repeat(40))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));

    assertNothingCreated(legalName, organizationsBefore, usersBefore, storesBefore);
  }

  /** The two handlers are transactional proxies on the one transaction manager. */
  @Test
  void shouldWrapTheRegistrationHandlerInATransaction() {
    assertThat(AopUtils.isAopProxy(registerOrganizationHandler)).isTrue();
  }

  private void assertNothingCreated(
      String legalName,
      long organizationsBefore,
      long usersBefore,
      long storesBefore
  ) {
    assertThat(count(
        "SELECT count(*) FROM organization.organizations "
            + "WHERE normalized_legal_name = ?",
        legalName.toUpperCase(Locale.ROOT)
    )).isZero();
    assertThat(count("SELECT count(*) FROM organization.organizations"))
        .isEqualTo(organizationsBefore);
    assertThat(count("SELECT count(*) FROM organization.stores"))
        .isEqualTo(storesBefore);
    assertThat(count("SELECT count(*) FROM identity.users"))
        .isEqualTo(usersBefore);
  }

  private ResultActions register(String legalName, String email, String password)
      throws Exception {
    String body = """
        {"organizationName": "Quincaillerie Thiès",
         "legalName": "%s",
         "currencyCode": "XOF",
         "headquartersCode": "THIES-01",
         "headquartersName": "Magasin principal",
         "ownerEmail": "%s",
         "ownerPassword": "%s"}
        """.formatted(legalName, email, password);
    return mockMvc.perform(
        post("/api/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    );
  }

  private static RegisterOrganizationCommand command(
      String legalName,
      String email,
      String password
  ) {
    return new RegisterOrganizationCommand(
        "Quincaillerie Thiès", legalName, "XOF", "THIES-01",
        "Magasin principal", email, password
    );
  }

  private long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }

  private static String uniqueLegalName() {
    return "Quincaillerie " + UUID.randomUUID();
  }

  private static String uniqueEmail() {
    return "owner-" + UUID.randomUUID() + "@Example.com";
  }
}
