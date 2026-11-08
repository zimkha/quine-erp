package com.zim.quine.login;

import com.jayway.jsonpath.JsonPath;
import com.zim.quine.PostgresContainerSupport;
import com.zim.quine.QuineApplication;
import com.zim.quine.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7b-AC1 and the failure side of T7b-AC2 on a real Postgres and the real
 * wiring: the owner registered through the public endpoint logs in and gets
 * a token a decoder with the test key can read. No security chain exists
 * yet (T7b-2): the token is issued, not yet honoured.
 */
@SpringBootTest(classes = QuineApplication.class)
@AutoConfigureMockMvc
class LoginIT {

  // Fictitious test values of the required length.
  private static final String PASSWORD = "not-a-real-password-1";
  private static final String WWW_AUTHENTICATE = "Bearer realm=\"quine-erp\"";

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

  @Test
  void shouldLogTheRegisteredOwnerInWhateverTheCaseAndSpacesOfTheEmail()
      throws Exception {
    String email = "Owner-" + UUID.randomUUID() + "@Example.com";
    UUID tenantId = register(email, PASSWORD);

    MvcResult result = login("  " + email.toUpperCase(Locale.ROOT) + "  ", PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(1800))
        .andReturn();
    String body = result.getResponse().getContentAsString();
    String token = JsonPath.read(body, "$.accessToken");

    Map<String, Object> user = jdbcTemplate.queryForMap(
        "SELECT id, password_hash FROM identity.users WHERE tenant_id = ?",
        tenantId
    );
    String hash = (String) user.get("password_hash");

    Jwt jwt = NimbusJwtDecoder
        .withSecretKey(new SecretKeySpec(TestJwtSecret.bytes(), "HmacSHA256"))
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
        .decode(token);
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getClaimAsString("tenantId")).isEqualTo(tenantId.toString());
    assertThat(jwt.getSubject()).isEqualTo(user.get("id").toString());
    assertThat(jwt.getAudience()).containsExactly("quine-tenant");
    assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(1800));
    assertThat(jwt.getClaims().keySet())
        .containsExactlyInAnyOrder("sub", "tenantId", "aud", "iat", "exp");

    String claims = jwt.getClaims().toString();
    assertThat(claims).doesNotContain(PASSWORD).doesNotContainIgnoringCase("example.com")
        .doesNotContain(hash);
    assertThat(body).doesNotContain(PASSWORD).doesNotContain(hash)
        .doesNotContainIgnoringCase("example.com");
  }

  @Test
  void shouldAnswerTheSame401ForEveryFailure() throws Exception {
    String email = "owner-" + UUID.randomUUID() + "@example.com";
    register(email, PASSWORD);
    String exactly72 = "a".repeat(72);
    String email72 = "owner72-" + UUID.randomUUID() + "@example.com";
    register(email72, exactly72);

    String[][] failures = {
        {"nobody-" + UUID.randomUUID() + "@example.com", PASSWORD},
        {email, "wrong-fake-password-9"},
        {email, "short-pw-1"},
        {email, "a".repeat(80)},
        {email, PASSWORD + "z".repeat(60)},
        {"not-an-email", PASSWORD},
        // The first 72 bytes match the stored password: BCrypt alone would accept.
        {email72, exactly72 + "b"},
    };

    String reference = null;
    for (String[] failure : failures) {
      MvcResult result = login(failure[0], failure[1])
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", WWW_AUTHENTICATE))
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
          .andReturn();
      String message = JsonPath.read(
          result.getResponse().getContentAsString(), "$.message");
      reference = reference == null ? message : reference;
      assertThat(message).isEqualTo(reference);
    }

    // And the 72-byte password itself still works.
    login(email72, exactly72).andExpect(status().isOk());
  }

  private UUID register(String email, String password) throws Exception {
    String body = """
        {"organizationName": "Quincaillerie Thiès",
         "legalName": "Quincaillerie %s",
         "currencyCode": "XOF",
         "headquartersCode": "THIES-01",
         "headquartersName": "Magasin principal",
         "ownerEmail": "%s",
         "ownerPassword": "%s"}
        """.formatted(UUID.randomUUID(), email, password);
    String response = mockMvc.perform(post("/api/organizations")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.tenantId"));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
  }
}
