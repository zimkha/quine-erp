package com.zim.quine.security;

import com.jayway.jsonpath.JsonPath;
import com.zim.shared.tenant.TenantNotResolvedException;
import com.zim.shared.web.ServletErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The entry point answers every authentication failure with one header and
 * one body, never an OAuth2 detail, and its code and message are parameters
 * (the tenant instance here; the platform chain will add its own).
 */
class ApiAuthenticationEntryPointTest {

  private static final String HEADER = "Bearer realm=\"quine-erp\"";

  private final ServletErrorWriter writer = new ServletErrorWriter(JsonMapper.builder().build());
  private final ApiAuthenticationEntryPoint tenant = new ApiAuthenticationEntryPoint(
      writer, TenantNotResolvedException.CODE, TenantNotResolvedException.MESSAGE);

  @Test
  void shouldAnswerTheSameHeaderAndBodyWhateverTheFailure() throws Exception {
    List<AuthenticationException> failures = List.of(
        new InsufficientAuthenticationException("Full authentication is required"),
        new BadCredentialsException("secret detail: bad signature"),
        new InvalidBearerTokenException("An error occurred while attempting to decode the Jwt: expired"),
        new OAuth2AuthenticationException(new BearerTokenError(
            BearerTokenErrorCodes.INVALID_TOKEN, org.springframework.http.HttpStatus.UNAUTHORIZED,
            "Jwt expired at 2026-01-01T00:00:00Z", "https://tools.ietf.org/html/rfc6750#section-3.1")),
        new OAuth2AuthenticationException(BearerTokenErrors_invalidRequest())
    );

    for (AuthenticationException failure : failures) {
      MockHttpServletResponse response = new MockHttpServletResponse();

      tenant.commence(new MockHttpServletRequest(), response, failure);

      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getHeaders("WWW-Authenticate")).containsExactly(HEADER);
      assertThat(response.getContentType()).isEqualTo("application/json");
      assertThat(response.getErrorMessage()).as("sendError is never used").isNull();
      String body = response.getContentAsString();
      assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
          .containsExactly("code", "message", "timestamp");
      assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("TENANT_NOT_RESOLVED");
      assertThat((String) JsonPath.read(body, "$.message")).isEqualTo(TenantNotResolvedException.MESSAGE);
      assertThat(body).doesNotContain("secret", "expired", "Jwt", "rfc6750", "invalid_token");
    }
  }

  @Test
  void shouldTakeTheCodeAndTheMessageFromTheInstance() throws Exception {
    ApiAuthenticationEntryPoint platform = new ApiAuthenticationEntryPoint(
        writer, "AUTHENTICATION_REQUIRED", "Authentication is required");
    MockHttpServletResponse response = new MockHttpServletResponse();

    platform.commence(new MockHttpServletRequest(), response,
        new InsufficientAuthenticationException("x"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeaders("WWW-Authenticate")).containsExactly(HEADER);
    String body = response.getContentAsString();
    assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("AUTHENTICATION_REQUIRED");
    assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("Authentication is required");
  }

  private static BearerTokenError BearerTokenErrors_invalidRequest() {
    return org.springframework.security.oauth2.server.resource.BearerTokenErrors
        .invalidRequest("Multiple Authorization headers");
  }
}
