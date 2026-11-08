package com.zim.organization.presentation.rest;

import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant self-activation route is off by default: the controller exists
 * only with {@code quine.organization.tenant-activation.enabled=true}. T9d
 * deletes the controller and this test.
 */
class TenantActivationFlagTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withBean(CurrentTenantProvider.class, () -> Mockito.mock(CurrentTenantProvider.class))
      .withBean(ActivateOrganizationHandler.class, () -> Mockito.mock(ActivateOrganizationHandler.class))
      .withUserConfiguration(TenantActivationController.class);

  @Test
  void shouldNotExistByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean(TenantActivationController.class));
  }

  @Test
  void shouldNotExistWhenTheFlagIsFalseOrAnythingButTrue() {
    for (String value : new String[] {"false", "", "yes", "TRUE1"}) {
      runner.withPropertyValues("quine.organization.tenant-activation.enabled=" + value)
          .run(context -> assertThat(context).doesNotHaveBean(TenantActivationController.class));
    }
  }

  @Test
  void shouldExistOnlyWhenTheFlagIsTrue() {
    runner.withPropertyValues("quine.organization.tenant-activation.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TenantActivationController.class));
  }
}
