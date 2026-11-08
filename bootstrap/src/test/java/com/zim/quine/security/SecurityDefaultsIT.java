package com.zim.quine.security;

import com.jayway.jsonpath.JsonPath;
import com.zim.probe.BoomProbeController;
import com.zim.quine.PostgresContainerSupport;
import com.zim.quine.QuineApplication;
import com.zim.quine.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7b-AC15 on a real server (own context, so that the startup logs can be
 * captured and the container's real error dispatch is exercised): no
 * generated password, no session, no form login, no HTTP Basic, and an
 * authenticated request keeps its framework 404 or 500 instead of becoming
 * a 401 through {@code /error}.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityDefaultsIT {

  // Fictitious test value of the required length.
  private static final String PASSWORD = "not-a-real-password-1";
  private static final String BEARER_REALM = "Bearer realm=\"quine-erp\"";

  private final HttpClient client = HttpClient.newHttpClient();
  private final List<HttpResponse<String>> responses = new ArrayList<>();

  @Test
  void shouldStartWithoutAGeneratedPasswordAndKeepTheFrameworkErrors(CapturedOutput output)
      throws Exception {
    try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
        QuineApplication.class, BoomProbeController.class)
        .web(WebApplicationType.SERVLET)
        .run(
            "--server.port=0",
            "--spring.main.banner-mode=off",
            "--spring.datasource.url=" + PostgresContainerSupport.POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + PostgresContainerSupport.POSTGRES.getUsername(),
            "--spring.datasource.password=" + PostgresContainerSupport.POSTGRES.getPassword(),
            "--quine.security.tenant.jwt.secret=" + TestJwtSecret.value()
        )) {
      WebServer server = ((ServletWebServerApplicationContext) context).getWebServer();
      String base = "http://127.0.0.1:" + server.getPort();

      assertThat(output.getAll().toLowerCase())
          .doesNotContain("generated security password")
          .doesNotContain("using generated");

      // Real registration and login over HTTP (both public, no cookie).
      String token = registerAndLogin(base);

      // An authenticated request keeps the framework's own answers.
      assertThat(send(base, "GET", "/api/nothing-here", "Bearer " + token, null).statusCode())
          .as("unmapped path, authenticated").isEqualTo(404);
      assertThat(send(base, "GET", "/api/probe/boom", "Bearer " + token, null).statusCode())
          .as("unexpected server error, authenticated").isEqualTo(500);
      // The same paths without a token are refused first.
      assertTenantNotResolved(send(base, "GET", "/api/nothing-here", null, null));
      assertTenantNotResolved(send(base, "GET", "/api/probe/boom", null, null));

      // A public route's own framework error (sendError, then the /error dispatch)
      // is not turned into a 401 either: 415 for a body type nobody accepts.
      assertThat(send(base, "POST", "/api/auth/login", null, "x", "text/plain").statusCode())
          .as("public route, unsupported media type").isEqualTo(415);

      // No form login page and no logout endpoint: the paths are plain unmapped paths.
      for (String path : List.of("/login", "/logout", "/oauth2/authorization/x")) {
        assertTenantNotResolved(send(base, "GET", path, null, null));
        assertTenantNotResolved(send(base, "POST", path, null, "{}"));
        assertThat(send(base, "GET", path, "Bearer " + token, null).statusCode())
            .as(path + " authenticated").isEqualTo(404);
      }

      // HTTP Basic is not offered: a Basic header is a plain 401 with the Bearer header only.
      HttpResponse<String> basic = send(base, "GET", "/api/nothing-here",
          "Basic " + java.util.Base64.getEncoder().encodeToString("user:pass".getBytes()), null);
      assertTenantNotResolved(basic);
      assertThat(basic.headers().allValues("WWW-Authenticate")).doesNotContain("Basic realm=\"Realm\"");

      // A token in the query string is not honoured.
      assertTenantNotResolved(send(base, "GET", "/api/nothing-here?access_token=" + token, null, null));

      // Never a session or a cookie, on any response of this run.
      assertThat(responses).isNotEmpty().allSatisfy(response -> {
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.body()).doesNotContain("JSESSIONID");
      });

      assertThat(output.getAll().toLowerCase()).doesNotContain("generated security password");
    }
  }

  private String registerAndLogin(String base) throws Exception {
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
    assertThat(send(base, "POST", "/api/organizations", null, registration).statusCode()).isEqualTo(201);

    HttpResponse<String> login = send(base, "POST", "/api/auth/login", null,
        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    assertThat(login.statusCode()).isEqualTo(200);
    return JsonPath.read(login.body(), "$.accessToken");
  }

  private HttpResponse<String> send(String base, String method, String path, String authorization, String body)
      throws Exception {
    return send(base, method, path, authorization, body, "application/json");
  }

  private HttpResponse<String> send(
      String base, String method, String path, String authorization, String body, String contentType)
      throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
        .header("Content-Type", contentType);
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    request.method(method, body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    responses.add(response);
    return response;
  }

  private static void assertTenantNotResolved(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly(BEARER_REALM);
    assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("TENANT_NOT_RESOLVED");
  }
}
