package com.zim.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ServletErrorWriterTest {

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final ServletErrorWriter writer = new ServletErrorWriter(mapper);

  @Test
  void shouldWriteTheStandardBodyWithoutSendError() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, HttpStatus.FORBIDDEN, "SOME_CODE", "Some message");

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getErrorMessage()).isNull();
    JsonNode body = mapper.readTree(response.getContentAsString());
    assertThat(body.propertyNames()).containsExactly("code", "message", "timestamp");
    assertThat(body.get("code").asString()).isEqualTo("SOME_CODE");
    assertThat(body.get("message").asString()).isEqualTo("Some message");
    assertThat(Instant.parse(body.get("timestamp").asString())).isNotNull();
  }

  @Test
  void shouldAddTheCommonAuthenticateHeaderToA401() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.writeUnauthorized(response, "SOME_CODE", "Some message");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeaders("WWW-Authenticate"))
        .containsExactly(GenericExceptionAdvice.WWW_AUTHENTICATE_VALUE);
  }

  @Test
  void shouldFormatTheTimestampLikeTheMapperDoesForTheAdvice() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    Instant before = Instant.now();

    writer.write(response, HttpStatus.UNAUTHORIZED, "C", "M");

    String written = mapper.readTree(response.getContentAsString()).get("timestamp").asString();
    // The advice returns an ApiErrorResponse serialised by the same mapper:
    // an ISO instant, never a number.
    String viaMapper = mapper.writeValueAsString(new ApiErrorResponse("C", "M", Instant.parse(written)));
    assertThat(viaMapper).contains("\"timestamp\":\"" + written + "\"");
    assertThat(Instant.parse(written)).isAfterOrEqualTo(before.minusSeconds(1));
  }
}
