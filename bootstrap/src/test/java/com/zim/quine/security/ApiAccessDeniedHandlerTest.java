package com.zim.quine.security;

import com.jayway.jsonpath.JsonPath;
import com.zim.shared.web.ServletErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7b-AC16. No live route produces a 403 before T8: the handler is built and
 * tested alone.
 */
class ApiAccessDeniedHandlerTest {

  private final ApiAccessDeniedHandler handler =
      new ApiAccessDeniedHandler(new ServletErrorWriter(JsonMapper.builder().build()));

  @Test
  void shouldWrite403WithTheStandardBodyAndNoOAuth2Detail() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(new MockHttpServletRequest(), response,
        new AccessDeniedException("insufficient_scope: details"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getHeaderNames()).doesNotContain("WWW-Authenticate");
    assertThat(response.getErrorMessage()).as("sendError is never used").isNull();
    String body = response.getContentAsString();
    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactly("code", "message", "timestamp");
    assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("ACCESS_DENIED");
    assertThat(body).doesNotContain("insufficient_scope", "details");
    assertThat(Instant.parse(JsonPath.read(body, "$.timestamp"))).isNotNull();
  }
}
