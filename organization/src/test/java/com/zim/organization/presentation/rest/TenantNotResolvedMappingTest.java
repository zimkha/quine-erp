package com.zim.organization.presentation.rest;

import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the TenantNotResolvedException -> 401 mapping through a test-only
 * controller, because no real endpoint consults the provider until T5.
 *
 * <p>The probe controller is a nested class of this test and is registered
 * only through {@code @Import} below; the test applications do not
 * component-scan, so it cannot leak into any other context. It lives in
 * the {@code presentation.rest} package so that the advice's
 * {@code basePackageClasses} scope applies to it.
 */
@WebMvcTest(TenantNotResolvedMappingTest.TenantProbeController.class)
@Import({
    TenantNotResolvedMappingTest.TenantProbeController.class,
    ApiExceptionHandler.class
})
class TenantNotResolvedMappingTest {

  private static final String CALLER_TENANT_ID =
      "9a4c2e71-5b3d-4f8a-b6c1-0d2e4f6a8b13";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldAnswerUnauthorizedWhenTenantIsNotResolved() throws Exception {
    String body = mockMvc.perform(
            get(TenantProbeController.PATH)
                .header("X-Tenant-Id", CALLER_TENANT_ID)
        )
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(
            "WWW-Authenticate",
            "Bearer realm=\"quine-erp\""
        ))
        .andExpect(jsonPath("$", aMapWithSize(3)))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"))
        .andExpect(jsonPath("$.message").value(
            "The tenant of the current request could not be resolved"
        ))
        .andExpect(jsonPath("$.timestamp").exists())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertThat(body)
        .doesNotContain(CALLER_TENANT_ID)
        .doesNotContain("X-Tenant-Id")
        .doesNotContain("Bearer")
        .doesNotContain("Exception")
        .doesNotContain("\tat ");
  }

  @Test
  void shouldApplyScopedAdviceToProbeController() {
    RestControllerAdvice advice =
        ApiExceptionHandler.class.getAnnotation(RestControllerAdvice.class);

    assertThat(advice.basePackageClasses())
        .containsExactly(OrganizationController.class);
    assertThat(
        HandlerTypePredicate
            .forBasePackageClass(advice.basePackageClasses())
            .test(TenantProbeController.class)
    ).isTrue();
  }

  @RestController
  static class TenantProbeController {

    static final String PATH = "/api/test/tenant-probe";

    private final CurrentTenantProvider currentTenantProvider;

    TenantProbeController(CurrentTenantProvider currentTenantProvider) {
      this.currentTenantProvider = currentTenantProvider;
    }

    @GetMapping(PATH)
    String probe() {
      TenantId tenantId = currentTenantProvider.currentTenant();
      return tenantId.value().toString();
    }
  }

  @TestConfiguration
  static class ThrowingTenantProviderConfiguration {

    @Bean
    CurrentTenantProvider currentTenantProvider() {
      return () -> {
        throw new TenantNotResolvedException();
      };
    }
  }
}
