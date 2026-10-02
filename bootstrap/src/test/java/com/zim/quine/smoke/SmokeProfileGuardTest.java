package com.zim.quine.smoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** The forgeable header must never be usable in a real environment. */
class SmokeProfileGuardTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "prod", "production", "staging", "PROD", "uat", "demo", "live", "qa", "preprod"
  })
  void shouldRefuseToStartWithANonLocalProfile(String nonLocal) {
    new ApplicationContextRunner()
        .withUserConfiguration(SmokeTenantProvider.class)
        .withInitializer(context ->
            context.getEnvironment().setActiveProfiles("smoke", nonLocal))
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining("must never run with");
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "dev", "test", "docker"})
  void shouldStartWithAnExplicitlyLocalProfile(String local) {
    new ApplicationContextRunner()
        .withUserConfiguration(SmokeTenantProvider.class)
        .withInitializer(context ->
            context.getEnvironment().setActiveProfiles("smoke", local))
        .run(context ->
            assertThat(context).hasSingleBean(SmokeTenantProvider.class));
  }

  @Test
  void shouldStartWithTheSmokeProfileAlone() {
    new ApplicationContextRunner()
        .withUserConfiguration(SmokeTenantProvider.class)
        .withInitializer(context ->
            context.getEnvironment().setActiveProfiles("smoke"))
        .run(context ->
            assertThat(context).hasSingleBean(SmokeTenantProvider.class));
  }
}
