package com.zim.identity.presentation.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.zim.identity.application.handler.LoginHandler;
import com.zim.identity.application.port.AccessTokenIssuer;
import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PlainPassword;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.identity.infrastructure.security.BcryptPasswordHasher;
import com.zim.identity.infrastructure.security.JwtAccessTokenIssuer;
import com.zim.identity.presentation.rest.exception.IdentityApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import com.zim.shared.web.WebSharedConfiguration;
import com.zim.shared.web.testing.AdviceAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The login endpoint with the real handler, BCrypt and token issuer over an
 * in-memory user (T7b-AC1/2/4/5 at the web level). No security chain.
 */
@WebMvcTest(AuthController.class)
@Import({
    AuthController.class,
    IdentityApiExceptionHandler.class,
    WebSharedConfiguration.class,
    AuthControllerTest.Wiring.class
})
class AuthControllerTest {

  private static final String EMAIL = "awa@example.com";
  // Fictitious test value of the required length.
  private static final String PASSWORD = "not-a-real-password-1";
  private static final String WWW_AUTHENTICATE = "Bearer realm=\"quine-erp\"";

  private static final SecretKey KEY = randomKey();
  private static final TenantId TENANT = TenantId.generate();
  private static final UserId USER = UserId.generate();

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ApplicationContext context;

  @TestConfiguration
  static class Wiring {

    @Bean
    LoginHandler loginHandler() {
      BcryptPasswordHasher bcrypt = new BcryptPasswordHasher();
      User owner = User.createOwner(
          USER, TENANT, new Email(EMAIL),
          bcrypt.hash(new PlainPassword(PASSWORD)),
          Instant.parse("2026-11-08T09:00:00Z")
      );
      UserRepository users = new UserRepository() {
        @Override
        public void save(User user) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByEmail(Email email) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsOwnerByTenantId(TenantId tenantId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
          return Optional.of(owner).filter(user -> user.email().equals(email));
        }
      };
      AccessTokenIssuer issuer = new JwtAccessTokenIssuer(
          new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
          Duration.ofMinutes(30)
      );
      return new LoginHandler(users, bcrypt, bcrypt, issuer, Instant::now);
    }
  }

  @Test
  void shouldLogInAndReturnTheToken() throws Exception {
    MvcResult result = login("  Awa@Example.COM ", PASSWORD)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(1800))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain(PASSWORD).doesNotContain(EMAIL)
        .doesNotContainIgnoringCase("hash").doesNotContain("$2");

    String token = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY)
        .macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    assertThat(jwt.getSubject()).isEqualTo(USER.value().toString());
    assertThat(jwt.getClaimAsString("tenantId")).isEqualTo(TENANT.value().toString());
    assertThat(jwt.getAudience()).containsExactly("quine-tenant");
    assertThat(jwt.getExpiresAt())
        .isEqualTo(jwt.getIssuedAt().plusSeconds(1800));
  }

  static Stream<Arguments> failures() {
    return Stream.of(
        Arguments.of("unknown e-mail", "nobody@example.com", PASSWORD),
        Arguments.of("wrong password", EMAIL, "wrong-fake-password-9"),
        Arguments.of("password shorter than 12", EMAIL, "short-pw-1"),
        Arguments.of("password of one character", EMAIL, "x"),
        Arguments.of("password over 72 bytes", EMAIL, "a".repeat(80)),
        Arguments.of("password over 72 bytes, right start", EMAIL, PASSWORD + "z".repeat(60)),
        Arguments.of("malformed e-mail", "not-an-email", PASSWORD),
        Arguments.of("non-ASCII e-mail", "é@example.com", PASSWORD),
        Arguments.of("e-mail of 254 characters, malformed", "a".repeat(254), PASSWORD)
    );
  }

  /** T7b-AC2: one answer for every kind of failure. Never 422, never 404. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("failures")
  void shouldAnswerTheSame401ForEveryFailure(
      String name,
      String email,
      String password
  ) throws Exception {
    MvcResult result = login(email, password)
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", WWW_AUTHENTICATE))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message")
            .value("The e-mail or the password is incorrect"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(com.jayway.jsonpath.JsonPath.<java.util.Map<String, Object>>read(body, "$").keySet())
        .containsExactlyInAnyOrder("code", "message", "timestamp");
    assertThat(body).doesNotContain(password).doesNotContain(email);
  }

  /** T7b-AC4: input validation is a 400 with the pinned texts. */
  static Stream<Arguments> invalidBodies() {
    return Stream.of(
        Arguments.of("{\"email\":\"\",\"password\":\"" + PASSWORD + "\"}",
            "email: must not be blank"),
        Arguments.of("{\"email\":\"   \",\"password\":\"" + PASSWORD + "\"}",
            "email: must not be blank"),
        Arguments.of("{\"email\":\"" + EMAIL + "\",\"password\":\"\"}",
            "password: must not be blank"),
        Arguments.of("{\"email\":\"" + EMAIL + "\",\"password\":\"     \"}",
            "password: must not be blank"),
        Arguments.of("{\"email\":\"" + EMAIL + "\"}",
            "password: must not be blank"),
        Arguments.of("{\"password\":\"" + PASSWORD + "\"}",
            "email: must not be blank"),
        Arguments.of("{\"email\":null,\"password\":null}",
            "email: must not be blank; password: must not be blank"),
        Arguments.of("{}",
            "email: must not be blank; password: must not be blank"),
        Arguments.of("{\"email\":\"" + "a".repeat(255) + "\",\"password\":\"" + PASSWORD + "\"}",
            "email: size must be between 0 and 254")
    );
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void shouldAnswer400WithThePinnedTextForInvalidInput(
      String body,
      String message
  ) throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(message));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "", "not json marker-secret-1", "{\"email\": \"marker-secret-1\"", "[]",
      "{\"email\": 12, \"password\": {\"marker-secret-1\": 1}}"
  })
  void shouldAnswer400WithoutEchoForAMissingOrMalformedBody(String body)
      throws Exception {
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain("marker-secret-1");
  }

  @Test
  void shouldAnswer400ForAnAbsentBody() throws Exception {
    mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
  }

  @Test
  void shouldNotEchoAnInvalidEmailInTheValidationError() throws Exception {
    String longEmail = "marker-email-" + "a".repeat(260);

    String response = login(longEmail, PASSWORD)
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();

    assertThat(response).doesNotContain("marker-email-");
  }

  @Test
  void shouldMapEachExceptionThroughExactlyOneAdvice() {
    AdviceAssertions.assertOneMappingPerException(context);
  }

  /**
   * T7b-AC5: with DEBUG on the web framework and on identity, neither the
   * password nor the token reaches a log line, for a success and a failure.
   */
  @Test
  void shouldNeverLogThePasswordOrTheTokenAtDebug() throws Exception {
    String secretPassword = "log-probe-password-" + UUID.randomUUID();
    Logger web = (Logger) LoggerFactory.getLogger("org.springframework.web");
    Logger identity = (Logger) LoggerFactory.getLogger("com.zim.identity");
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Level webLevel = web.getLevel();
    Level identityLevel = identity.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    web.setLevel(Level.DEBUG);
    identity.setLevel(Level.DEBUG);
    String token;
    try {
      String body = login(EMAIL, PASSWORD).andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
      token = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
      login(EMAIL, secretPassword).andExpect(status().isUnauthorized());
      login("nobody@example.com", secretPassword).andExpect(status().isUnauthorized());
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"\",\"password\":\"" + secretPassword + "\"}"))
          .andExpect(status().isBadRequest());
    } finally {
      root.detachAppender(appender);
      web.setLevel(webLevel);
      identity.setLevel(identityLevel);
    }

    List<String> lines = new ArrayList<>();
    for (ILoggingEvent event : appender.list) {
      lines.add(event.getFormattedMessage());
      for (IThrowableProxy proxy = event.getThrowableProxy();
           proxy != null; proxy = proxy.getCause()) {
        lines.add(String.valueOf(proxy.getMessage()));
      }
    }
    assertThat(lines).isNotEmpty();
    assertThat(String.join("\n", lines))
        .doesNotContain(PASSWORD)
        .doesNotContain(secretPassword)
        .doesNotContain(token);
  }

  private ResultActions login(String email, String password) throws Exception {
    String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    return mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private static SecretKey randomKey() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
