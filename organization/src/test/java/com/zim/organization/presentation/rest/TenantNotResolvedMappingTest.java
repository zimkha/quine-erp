package com.zim.organization.presentation.rest;

import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerTypePredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TenantNotResolvedException -> 401 mapping itself is a shared contract
 * and is tested in web-shared (a probe controller outside organization);
 * its application to organization endpoints is pinned by
 * {@code ErrorContractCharacterizationTest}. What stays here is the scope of
 * the organization advice.
 *
 * <p>The probe controller lives in the {@code presentation.rest} package so
 * that the advice's {@code basePackageClasses} scope applies to it.
 */
class TenantNotResolvedMappingTest {

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
}
