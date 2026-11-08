package com.zim.shared.web;

import com.zim.shared.domain.BusinessRule;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.DomainException;
import com.zim.shared.tenant.TenantNotResolvedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7b-0 AC3: the generic mapping applies to a controller of any module. The
 * probe controller is outside {@code com.zim.organization}, and no module
 * advice is imported: only {@link WebSharedConfiguration}.
 */
@WebMvcTest(GenericExceptionAdviceTest.ProbeController.class)
@Import({
    GenericExceptionAdviceTest.ProbeController.class,
    WebSharedConfiguration.class
})
class GenericExceptionAdviceTest {

  private static final String RAW_INPUT = "SECRET-RAW-INPUT-1f9d";
  private static final String TIMESTAMP_PATTERN =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldAnswer400WithSortedDistinctPartsForInvalidDto() throws Exception {
    ResultActions result = mockMvc.perform(
        post("/api/test/probe/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\",\"label\":\"x\"}")
    );

    expectError(result, 400, "VALIDATION_FAILED",
        "label: size must be between 2 and 5; "
            + "name: must not be blank; "
            + "quantity: must not be null");
  }

  @Test
  void shouldAnswer400ForMalformedPathId() throws Exception {
    ResultActions result = mockMvc.perform(
        get("/api/test/probe/by-id/" + RAW_INPUT));

    expectError(result, 400, "VALIDATION_FAILED",
        "id: must be a valid UUID");
    assertNoLeak(result, RAW_INPUT);
  }

  @Test
  void shouldAnswer400ForMissingBody() throws Exception {
    expectError(
        mockMvc.perform(post("/api/test/probe/validate")
            .contentType(MediaType.APPLICATION_JSON)),
        400, "VALIDATION_FAILED", "Request body is missing or malformed");
  }

  @Test
  void shouldAnswer400ForMalformedBody() throws Exception {
    ResultActions result = mockMvc.perform(
        post("/api/test/probe/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"" + RAW_INPUT));

    expectError(result, 400, "VALIDATION_FAILED",
        "Request body is missing or malformed");
    assertNoLeak(result, RAW_INPUT);
  }

  @Test
  void shouldAnswer401WithChallengeHeaderWhenTenantIsNotResolved()
      throws Exception {
    ResultActions result = mockMvc.perform(
        get("/api/test/probe/tenant").header("X-Tenant-Id", RAW_INPUT));

    expectError(result, 401, "TENANT_NOT_RESOLVED",
        "The tenant of the current request could not be resolved");
    result.andExpect(header().string(
        "WWW-Authenticate", GenericExceptionAdvice.WWW_AUTHENTICATE_VALUE));
    assertThat(GenericExceptionAdvice.WWW_AUTHENTICATE_VALUE)
        .isEqualTo("Bearer realm=\"quine-erp\"");
    assertNoLeak(result, RAW_INPUT, "Bearer");
  }

  @Test
  void shouldAnswer409WithRuleCodeAndMessageForBrokenBusinessRule()
      throws Exception {
    ResultActions result = mockMvc.perform(get("/api/test/probe/rule"));

    expectError(result, 409, "PROBE_RULE_BROKEN", "The probe rule is broken");
    result.andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void shouldAnswer422WithCodeAndMessageOfDomainException()
      throws Exception {
    ResultActions result = mockMvc.perform(get("/api/test/probe/domain"));

    expectError(result, 422, "PROBE_DOMAIN_ERROR",
        "The probe value is not accepted");
    result.andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void shouldAnswer409ForOptimisticLockingFailure() throws Exception {
    ResultActions result = mockMvc.perform(get("/api/test/probe/locking"));

    expectError(result, 409, "CONCURRENT_MODIFICATION",
        "The resource was modified by another request; "
            + "reload it and retry");
    assertNoLeak(result, "select ", "probe_table");
    result.andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void shouldAnswer409WithoutSqlForDataIntegrityViolation()
      throws Exception {
    ResultActions result = mockMvc.perform(get("/api/test/probe/integrity"));

    expectError(result, 409, "DATA_INTEGRITY_VIOLATION",
        "The request conflicts with existing data");
    assertNoLeak(result, "insert into", "probe_table", "uk_probe");
    result.andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  private static void expectError(
      ResultActions result,
      int status,
      String code,
      String message
  ) throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(
            MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.message").value(message))
        .andExpect(jsonPath("$.timestamp").value(
            matchesPattern(TIMESTAMP_PATTERN)));
  }

  private static void assertNoLeak(ResultActions result, String... fragments)
      throws Exception {
    String body = result.andReturn().getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain(fragments)
        .doesNotContain("X-Tenant-Id")
        .doesNotContain("Exception")
        .doesNotContain("\tat ");
  }

  record ProbeBody(
      @NotBlank String name,
      @Size(min = 2, max = 5) String label,
      @NotNull Integer quantity
  ) {
  }

  static final class ProbeRule implements BusinessRule {

    @Override
    public boolean isBroken() {
      return true;
    }

    @Override
    public String code() {
      return "PROBE_RULE_BROKEN";
    }

    @Override
    public String message() {
      return "The probe rule is broken";
    }
  }

  static final class ProbeDomainException extends DomainException {

    ProbeDomainException() {
      super("PROBE_DOMAIN_ERROR", "The probe value is not accepted");
    }
  }

  @RestController
  static class ProbeController {

    @PostMapping("/api/test/probe/validate")
    String validate(@Valid @RequestBody ProbeBody body) {
      return "ok";
    }

    @GetMapping("/api/test/probe/by-id/{id}")
    String byId(@PathVariable UUID id) {
      return id.toString();
    }

    @GetMapping("/api/test/probe/tenant")
    String tenant() {
      throw new TenantNotResolvedException();
    }

    @GetMapping("/api/test/probe/rule")
    String rule() {
      throw new BusinessRuleViolationException(new ProbeRule());
    }

    @GetMapping("/api/test/probe/domain")
    String domain() {
      throw new ProbeDomainException();
    }

    @GetMapping("/api/test/probe/locking")
    String locking() {
      throw new OptimisticLockingFailureException(
          "select version from probe_table where id = 1");
    }

    @GetMapping("/api/test/probe/integrity")
    String integrity() {
      throw new DataIntegrityViolationException(
          "insert into probe_table violates uk_probe");
    }
  }
}
