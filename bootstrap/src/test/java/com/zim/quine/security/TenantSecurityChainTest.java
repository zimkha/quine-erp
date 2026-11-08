package com.zim.quine.security;

import com.jayway.jsonpath.JsonPath;
import com.zim.identity.application.handler.LoginHandler;
import com.zim.identity.infrastructure.configuration.TenantJwtConfiguration;
import com.zim.identity.presentation.rest.AuthController;
import com.zim.identity.presentation.rest.exception.IdentityApiExceptionHandler;
import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.command.AddStoreCommand;
import com.zim.organization.application.command.ChangeHeadquartersCommand;
import com.zim.organization.application.command.CloseOrganizationCommand;
import com.zim.organization.application.command.DeactivateStoreCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.handler.CloseOrganizationHandler;
import com.zim.organization.application.handler.DeactivateStoreHandler;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.application.result.AddStoreResult;
import com.zim.organization.application.result.ChangeHeadquartersResult;
import com.zim.organization.application.result.CloseOrganizationResult;
import com.zim.organization.application.result.DeactivateStoreResult;
import com.zim.organization.presentation.rest.OrganizationController;
import com.zim.organization.presentation.rest.TenantActivationController;
import com.zim.organization.presentation.rest.TenantOrganizationController;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.quine.TestJwtSecret;
import com.zim.quine.TestTokens;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.TenantNotResolvedException;
import com.zim.shared.web.WebSharedConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The real security chain (T7b-2) over the real tenant routes, the real
 * decoder and the real provider, with the application handlers mocked. A
 * slice: it sees only the controllers imported here (the full-context
 * route walk is {@code RouteWalkingIT}).
 */
@WebMvcTest(
    controllers = {
        OrganizationController.class,
        TenantOrganizationController.class,
        TenantActivationController.class,
        AuthController.class
    },
    properties = "quine.organization.tenant-activation.enabled=true"
)
@Import({
    TenantSecurityConfiguration.class,
    TenantJwtConfiguration.class,
    WebSharedConfiguration.class,
    ApiExceptionHandler.class,
    IdentityApiExceptionHandler.class,
    OrganizationController.class,
    TenantOrganizationController.class,
    TenantActivationController.class,
    AuthController.class
})
public class TenantSecurityChainTest {

  static final String BEARER_REALM = "Bearer realm=\"quine-erp\"";
  private static final UUID TENANT_A = UUID.fromString("2d3a7d37-ef2c-4794-b248-b08acf42eb38");
  private static final UUID TENANT_B = UUID.fromString("9a4c2e71-5b3d-4f8a-b6c1-0d2e4f6a8b13");
  private static final UUID ORGANIZATION = UUID.fromString("5c80d578-83f7-4b44-b5f7-598530067a09");
  private static final String STORES = "/api/organizations/{id}/stores";
  private static final String VALID_BODY = "{\"storeCode\": \"thies-02\", \"storeName\": \"Magasin 2\"}";

  @DynamicPropertySource
  static void configureKey(DynamicPropertyRegistry registry) {
    registry.add("quine.security.tenant.jwt.secret", TestJwtSecret::value);
  }

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  AddStoreHandler addStoreHandler;
  @MockitoBean
  ChangeHeadquartersHandler changeHeadquartersHandler;
  @MockitoBean
  DeactivateStoreHandler deactivateStoreHandler;
  @MockitoBean
  CloseOrganizationHandler closeOrganizationHandler;
  @MockitoBean
  ActivateOrganizationHandler activateOrganizationHandler;
  @MockitoBean
  RegisterOrganizationHandler registerOrganizationHandler;
  @MockitoBean
  LoginHandler loginHandler;

  @BeforeEach
  void stubHandlers() {
    Instant now = Instant.parse("2026-11-08T10:00:00Z");
    when(addStoreHandler.handle(any())).thenReturn(new AddStoreResult(
        ORGANIZATION, UUID.randomUUID(), "THIES-02", "Magasin 2", false, true, now));
    when(changeHeadquartersHandler.handle(any())).thenReturn(
        new ChangeHeadquartersResult(ORGANIZATION, UUID.randomUUID(), now));
    when(deactivateStoreHandler.handle(any())).thenReturn(
        new DeactivateStoreResult(ORGANIZATION, UUID.randomUUID(), false, now));
    when(closeOrganizationHandler.handle(any())).thenReturn(
        new CloseOrganizationResult(ORGANIZATION, TENANT_A, "CLOSED", now));
    when(activateOrganizationHandler.handle(any())).thenReturn(
        new ActivateOrganizationResult(ORGANIZATION, TENANT_A, "ACTIVE", now));
  }

  // --- T7b-AC9: every refusal is the same 401 --------------------------------

  static Stream<Arguments> refusedAuthentications() {
    Map<String, Object> expired = TestTokens.claims(TENANT_A);
    expired.put("iat", Instant.now().minus(Duration.ofMinutes(30)));
    expired.put("exp", Instant.now().minus(Duration.ofMinutes(10)));
    Map<String, Object> notYetValid = TestTokens.claims(TENANT_A);
    notYetValid.put("nbf", Instant.now().plus(Duration.ofMinutes(10)));
    Map<String, Object> platformAudience = TestTokens.claims(TENANT_A);
    platformAudience.put("aud", List.of("quine-platform"));
    Map<String, Object> noAudience = TestTokens.claims(TENANT_A);
    noAudience.remove("aud");
    Map<String, Object> noTenant = TestTokens.claims(TENANT_A);
    noTenant.remove("tenantId");

    String good = TestTokens.valid(TENANT_A);
    String basic = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());

    return Stream.of(
        refused("no token", request -> { }),
        refused("invalid signature", bearer(TestTokens.sign(TestTokens.randomKey(32), TestTokens.claims(TENANT_A)))),
        refused("signed with another algorithm and key",
            bearer(TestTokens.sign(TestTokens.randomKey(64), MacAlgorithm.HS384, TestTokens.claims(TENANT_A)))),
        refused("unsigned token (alg none)", bearer(TestTokens.unsigned(TENANT_A))),
        refused("expired", bearer(TestTokens.sign(TestJwtSecret.bytes(), expired))),
        refused("not yet valid (nbf)", bearer(TestTokens.sign(TestJwtSecret.bytes(), notYetValid))),
        refused("another audience", bearer(TestTokens.sign(TestJwtSecret.bytes(), platformAudience))),
        refused("no audience", bearer(TestTokens.sign(TestJwtSecret.bytes(), noAudience))),
        refused("tenantId absent", bearer(TestTokens.sign(TestJwtSecret.bytes(), noTenant))),
        refused("tenantId not a UUID", bearer(withTenantClaim("not-a-uuid"))),
        refused("tenantId empty", bearer(withTenantClaim(""))),
        refused("tenantId 1-1-1-1-1 (accepted by UUID.fromString)", bearer(withTenantClaim("1-1-1-1-1"))),
        refused("tenantId upper case", bearer(withTenantClaim(TENANT_A.toString().toUpperCase()))),
        refused("tenantId padded with spaces", bearer(withTenantClaim(" " + TENANT_A + " "))),
        refused("tenantId a number", bearer(withTenantClaim(12345))),
        refused("tenantId a list", bearer(withTenantClaim(List.of(TENANT_A.toString())))),
        refused("Basic header", request -> request.header(HttpHeaders.AUTHORIZATION, basic)),
        refused("malformed Bearer (not a JWT)", request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")),
        refused("malformed Bearer (garbage)", request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer !!!")),
        refused("Bearer with two tokens", request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + good + " " + good)),
        refused("Bearer without a token", request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer")),
        refused("Bearer with an empty token", request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer ")),
        refused("empty Authorization header", request -> request.header(HttpHeaders.AUTHORIZATION, "")),
        refused("two Authorization headers, valid first",
            request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + good, "Bearer " + good)),
        refused("two Authorization headers, valid then garbage",
            request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + good, "Bearer garbage")),
        refused("two Authorization headers, Basic then valid",
            request -> request.header(HttpHeaders.AUTHORIZATION, basic, "Bearer " + good)),
        refused("valid token in another header", request -> request.header("X-Authorization", "Bearer " + good))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedAuthentications")
  void shouldAnswerTheSame401ToEveryRefusedAuthentication(
      String name,
      Consumer<MockHttpServletRequestBuilder> authentication
  ) throws Exception {
    MockHttpServletRequestBuilder request = post(STORES, ORGANIZATION)
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY);
    authentication.accept(request);

    MvcResult result = mockMvc.perform(request).andReturn();

    assertTenantNotResolved(result);
    verifyNoHandlerCalled();
  }

  /**
   * The default resolver would read an access_token query parameter only on a
   * GET, so this is the request that fails if the parameter is ever allowed.
   * Nothing is mapped for this GET: an accepted token would answer 404 or 405,
   * never the 401 below.
   */
  @Test
  void shouldIgnoreATokenGivenAsAnAccessTokenQueryParameterOnAGet() throws Exception {
    String good = TestTokens.sign(TestJwtSecret.bytes(), TestTokens.claims(TENANT_A));

    MvcResult result = mockMvc.perform(
        get("/api/organizations/{id}", ORGANIZATION).queryParam("access_token", good)
    ).andReturn();

    assertTenantNotResolved(result);
    verifyNoHandlerCalled();
  }

  /**
   * The default resolver would read an access_token form field only for an
   * application/x-www-form-urlencoded body, so the content type must be a form
   * here. An accepted token would reach the JSON-only handler and answer 415.
   */
  @Test
  void shouldIgnoreATokenGivenAsAFormBodyParameter() throws Exception {
    String good = TestTokens.sign(TestJwtSecret.bytes(), TestTokens.claims(TENANT_A));

    MvcResult result = mockMvc.perform(
        post(STORES, ORGANIZATION)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("access_token", good)
    ).andReturn();

    assertTenantNotResolved(result);
    verifyNoHandlerCalled();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tenantRoutes")
  void shouldRefuseEveryTenantRouteWithoutToken(
      String name,
      MockHttpServletRequestBuilder request
  ) throws Exception {
    assertTenantNotResolved(mockMvc.perform(request).andReturn());
    verifyNoHandlerCalled();
  }

  static Stream<Arguments> tenantRoutes() {
    return Stream.of(
        Arguments.of("add store", post(STORES, ORGANIZATION)
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)),
        Arguments.of("change headquarters", put("/api/organizations/{id}/headquarters", ORGANIZATION)
            .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\": \"" + UUID.randomUUID() + "\"}")),
        Arguments.of("deactivate store", post("/api/organizations/{id}/stores/{storeId}/deactivation",
            ORGANIZATION, UUID.randomUUID())),
        Arguments.of("close", post("/api/organizations/{id}/closure", ORGANIZATION)),
        Arguments.of("activate", post("/api/organizations/{id}/activation", ORGANIZATION))
    );
  }

  @Test
  void shouldNotExposeAnyAuthenticationDetailInTheRefusal() throws Exception {
    MvcResult result = mockMvc.perform(post(STORES, ORGANIZATION)
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.sign(
                TestTokens.randomKey(32), TestTokens.claims(TENANT_A))))
        .andReturn();

    String everything = result.getResponse().getContentAsString()
        + result.getResponse().getHeaderNames().stream()
        .map(name -> name + "=" + result.getResponse().getHeaders(name))
        .reduce("", String::concat);
    assertThat(everything.toLowerCase())
        .doesNotContain("invalid_token", "error=", "error_description", "signature",
            "jwt", "exception", "oauth", "rfc6750");
  }

  // --- T7b-AC10: 401 before 400, and the public routes keep 400 --------------

  @Test
  void shouldAnswer401BeforeValidationOfTheBody() throws Exception {
    assertTenantNotResolved(mockMvc.perform(post(STORES, ORGANIZATION)
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());
    assertTenantNotResolved(mockMvc.perform(post(STORES, ORGANIZATION)
        .contentType(MediaType.APPLICATION_JSON).content("not json")).andReturn());
    verifyNoHandlerCalled();
  }

  @Test
  void shouldAnswer401BeforeThePathIdIsParsed() throws Exception {
    assertTenantNotResolved(mockMvc.perform(post(STORES, "not-a-uuid")
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn());
    assertTenantNotResolved(mockMvc.perform(
        post("/api/organizations/{id}/closure", "not-a-uuid")).andReturn());
    verifyNoHandlerCalled();
  }

  @Test
  void shouldAnswer400ForTheSameRequestsWithAValidToken() throws Exception {
    String token = "Bearer " + TestTokens.valid(TENANT_A);

    MvcResult emptyBody = mockMvc.perform(post(STORES, ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, token)
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    MvcResult badId = mockMvc.perform(post(STORES, "not-a-uuid")
        .header(HttpHeaders.AUTHORIZATION, token)
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn();

    for (MvcResult result : List.of(emptyBody, badId)) {
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("VALIDATION_FAILED");
    }
    verifyNoInteractions(addStoreHandler);
  }

  @Test
  void shouldKeepAnswering400OnThePublicRoutesWithoutToken() throws Exception {
    MvcResult registration = mockMvc.perform(post("/api/organizations")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    MvcResult login = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    MvcResult unreadable = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content("not json")).andReturn();

    for (MvcResult result : List.of(registration, login, unreadable)) {
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("VALIDATION_FAILED");
    }
    verifyNoInteractions(registerOrganizationHandler, loginHandler);
  }

  /**
   * Standard resource-server behaviour, pinned here: a public route is not
   * token-free for a request that carries a bad token. A client must send no
   * Authorization header (not a stale one) to register or log in.
   */
  @Test
  void shouldRefuseABadTokenEvenOnAPublicRoute() throws Exception {
    assertTenantNotResolved(mockMvc.perform(post("/api/auth/login")
        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());
    assertTenantNotResolved(mockMvc.perform(post("/api/organizations")
        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());

    MvcResult withValidToken = mockMvc.perform(post("/api/auth/login")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(TENANT_A))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    assertThat(withValidToken.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldAnswerATokenlessCsrfFreePostOnTheRegistrationRoute() throws Exception {
    // A POST without CSRF token must reach the controller (400 here), not a 403.
    MvcResult result = mockMvc.perform(post("/api/organizations")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldNotCreateASessionNorACookie() throws Exception {
    MvcResult authenticated = mockMvc.perform(post(STORES, ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(TENANT_A))
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn();

    assertThat(authenticated.getResponse().getStatus()).isEqualTo(201);
    assertThat(authenticated.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    assertThat(authenticated.getRequest().getSession(false)).isNull();
  }

  // --- T7b-AC8 and T7b-AC12: the tenant is the claim, nothing else -----------

  @Test
  void shouldActForTheTenantOfTheToken() throws Exception {
    MvcResult result = mockMvc.perform(post(STORES, ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(TENANT_A))
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    ArgumentCaptor<AddStoreCommand> command = ArgumentCaptor.forClass(AddStoreCommand.class);
    verify(addStoreHandler).handle(command.capture());
    assertThat(command.getValue().tenantId()).isEqualTo(new TenantId(TENANT_A));
    assertThat(command.getValue().organizationId()).isEqualTo(ORGANIZATION);
  }

  @Test
  void shouldIgnoreEveryTenantFromThePathTheQueryTheBodyAndTheHeaders() throws Exception {
    // The path id is the organization id: it carries tenant B's UUID here, and
    // B's UUID is also sent in the query, the body and four headers.
    String body = "{\"storeCode\": \"thies-02\", \"storeName\": \"Magasin 2\", "
        + "\"tenantId\": \"" + TENANT_B + "\"}";

    MvcResult result = mockMvc.perform(post(STORES, TENANT_B)
        .queryParam("tenantId", TENANT_B.toString())
        .header("X-Smoke-Tenant", TENANT_B.toString())
        .header("X-Tenant-Id", TENANT_B.toString())
        .header("X-Tenant", TENANT_B.toString())
        .header("Tenant-Id", TENANT_B.toString())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(TENANT_A))
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    ArgumentCaptor<AddStoreCommand> command = ArgumentCaptor.forClass(AddStoreCommand.class);
    verify(addStoreHandler).handle(command.capture());
    assertThat(command.getValue().tenantId()).isEqualTo(new TenantId(TENANT_A));
  }

  @Test
  void shouldNotLetTheSmokeOrTenantHeadersAuthenticateARequest() throws Exception {
    MvcResult result = mockMvc.perform(post(STORES, ORGANIZATION)
        .header("X-Smoke-Tenant", TENANT_A.toString())
        .header("X-Tenant-Id", TENANT_A.toString())
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn();

    assertTenantNotResolved(result);
    verifyNoHandlerCalled();
  }

  @Test
  void shouldPassTheTenantOfTheTokenToEveryTenantRoute() throws Exception {
    String token = "Bearer " + TestTokens.valid(TENANT_A);
    UUID storeId = UUID.randomUUID();

    assertThat(mockMvc.perform(put("/api/organizations/{id}/headquarters", ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\": \"" + storeId + "\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mockMvc.perform(post("/api/organizations/{id}/stores/{storeId}/deactivation", ORGANIZATION, storeId)
        .header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mockMvc.perform(post("/api/organizations/{id}/closure", ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mockMvc.perform(post("/api/organizations/{id}/activation", ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus()).isEqualTo(200);

    TenantId tenantA = new TenantId(TENANT_A);
    ArgumentCaptor<ChangeHeadquartersCommand> headquarters = ArgumentCaptor.forClass(ChangeHeadquartersCommand.class);
    verify(changeHeadquartersHandler).handle(headquarters.capture());
    assertThat(headquarters.getValue().tenantId()).isEqualTo(tenantA);
    ArgumentCaptor<DeactivateStoreCommand> deactivation = ArgumentCaptor.forClass(DeactivateStoreCommand.class);
    verify(deactivateStoreHandler).handle(deactivation.capture());
    assertThat(deactivation.getValue().tenantId()).isEqualTo(tenantA);
    ArgumentCaptor<CloseOrganizationCommand> closure = ArgumentCaptor.forClass(CloseOrganizationCommand.class);
    verify(closeOrganizationHandler).handle(closure.capture());
    assertThat(closure.getValue().tenantId()).isEqualTo(tenantA);
    ArgumentCaptor<ActivateOrganizationCommand> activation = ArgumentCaptor.forClass(ActivateOrganizationCommand.class);
    verify(activateOrganizationHandler).handle(activation.capture());
    assertThat(activation.getValue().tenantId()).isEqualTo(tenantA);
  }

  // --- T7b-AC19: the filter writes the format of the advice ------------------

  @Test
  void shouldWriteTheSameTimestampFormatAsTheAdvice() throws Exception {
    MvcResult filter = mockMvc.perform(post(STORES, ORGANIZATION)
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andReturn();
    MvcResult advice = mockMvc.perform(post(STORES, ORGANIZATION)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(TENANT_A))
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

    String iso = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";
    assertThat(filter.getResponse().getStatus()).isEqualTo(401);
    assertThat(advice.getResponse().getStatus()).isEqualTo(400);
    for (MvcResult result : List.of(filter, advice)) {
      String body = result.getResponse().getContentAsString();
      assertThat((String) JsonPath.read(body, "$.timestamp")).matches(iso);
      assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
          .containsExactly("code", "message", "timestamp");
      assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }
  }

  // --- helpers ---------------------------------------------------------------

  private static Arguments refused(String name, Consumer<MockHttpServletRequestBuilder> authentication) {
    return Arguments.of(name, authentication);
  }

  private static Consumer<MockHttpServletRequestBuilder> bearer(String token) {
    return request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static String withTenantClaim(Object value) {
    Map<String, Object> claims = TestTokens.claims(TENANT_A);
    claims.put("tenantId", value);
    return TestTokens.sign(TestJwtSecret.bytes(), claims);
  }

  private void verifyNoHandlerCalled() {
    verifyNoInteractions(
        addStoreHandler,
        changeHeadquartersHandler,
        deactivateStoreHandler,
        closeOrganizationHandler,
        activateOrganizationHandler,
        registerOrganizationHandler,
        loginHandler
    );
  }

  public static void assertTenantNotResolved(MvcResult result) throws Exception {
    var response = result.getResponse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeaders(HttpHeaders.WWW_AUTHENTICATE)).containsExactly(BEARER_REALM);
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    String body = response.getContentAsString();
    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactly("code", "message", "timestamp");
    assertThat((String) JsonPath.read(body, "$.code")).isEqualTo(TenantNotResolvedException.CODE);
    assertThat((String) JsonPath.read(body, "$.message")).isEqualTo(TenantNotResolvedException.MESSAGE);
    assertThat(Instant.parse(JsonPath.read(body, "$.timestamp"))).isNotNull();
  }
}
