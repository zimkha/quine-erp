package com.zim.identity.infrastructure.configuration;

import com.zim.identity.application.port.AccessTokenIssuer;
import com.zim.identity.infrastructure.security.TenantJwtSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7b-AC6: the context refuses to start without a usable key, and the
 * message names the variable but never contains the value. No database: the
 * runner loads only the token configuration. The keys are random per run.
 */
class TenantJwtConfigurationTest {

  private static final String SECRET = "quine.security.tenant.jwt.secret";
  private static final String TTL = "quine.security.tenant.jwt.ttl-minutes";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TenantJwtConfiguration.class);

  @Test
  void shouldStartWithAValidKeyAndTheDefaultLifetime() {
    runner.withPropertyValues(SECRET + "=" + randomKey(32)).run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(AccessTokenIssuer.class);
      assertThat(context).hasBean("tenantJwtEncoder");
      assertThat(context.getBean(JwtEncoder.class)).isNotNull();
      assertThat(context.getBean(TenantJwtSettings.class).ttl().toMinutes())
          .isEqualTo(30);
    });
  }

  @Test
  void shouldAcceptALongerKey() {
    runner.withPropertyValues(SECRET + "=" + randomKey(64))
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void shouldRefuseAMissingKeyNamingTheVariable() {
    runner.run(context -> {
      assertThat(context).hasFailed();
      assertThat(rootMessages(context.getStartupFailure()))
          .contains("QUINE_JWT_SECRET is required");
    });
  }

  @Test
  void shouldRefuseAnUnresolvedPlaceholderNamingTheVariable() {
    // What application.yaml gives when the variable is not exported.
    runner.withPropertyValues(SECRET + "=${QUINE_JWT_SECRET_NOT_SET_IN_TEST}")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(rootMessages(context.getStartupFailure()))
              .contains("QUINE_JWT_SECRET is required");
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void shouldRefuseAnEmptyOrBlankKey(String value) {
    runner.withPropertyValues(SECRET + "=" + value).run(context -> {
      assertThat(context).hasFailed();
      assertThat(rootMessages(context.getStartupFailure()))
          .contains("QUINE_JWT_SECRET is required");
    });
  }

  @Test
  void shouldRefuseAKeyThatIsNotBase64WithoutPrintingIt() {
    // Random Base64 with a character that is not Base64 appended.
    String value = randomKey(32) + "!";
    runner.withPropertyValues(SECRET + "=" + value).run(context -> {
      assertThat(context).hasFailed();
      String messages = rootMessages(context.getStartupFailure());
      assertThat(messages).contains("QUINE_JWT_SECRET is not valid");
      assertNoFragmentOf(value, messages);
      assertThat(causes(context.getStartupFailure()))
          .noneMatch(cause -> cause instanceof IllegalArgumentException);
    });
  }

  @Test
  void shouldRefuseAKeyThatIsTooShortWithoutPrintingIt() {
    String value = randomKey(31);
    runner.withPropertyValues(SECRET + "=" + value).run(context -> {
      assertThat(context).hasFailed();
      String messages = rootMessages(context.getStartupFailure());
      assertThat(messages).contains("QUINE_JWT_SECRET is too short");
      assertNoFragmentOf(value, messages);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "121", "-5", "abc", "30.5", "1h"})
  void shouldRefuseALifetimeOutOfBoundsOrNotANumber(String ttl) {
    runner.withPropertyValues(SECRET + "=" + randomKey(32), TTL + "=" + ttl)
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(rootMessages(context.getStartupFailure()))
              .contains("ttl-minutes must be a whole number of minutes between 1 and 120");
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "15", "120"})
  void shouldAcceptALifetimeWithinBounds(String ttl) {
    runner.withPropertyValues(SECRET + "=" + randomKey(32), TTL + "=" + ttl)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(TenantJwtSettings.class).ttl().toMinutes())
              .isEqualTo(Long.parseLong(ttl));
        });
  }

  @Test
  void shouldNeverPrintTheKeyInToString() {
    String value = randomKey(32);
    runner.withPropertyValues(SECRET + "=" + value).run(context ->
        assertThat(context.getBean(TenantJwtSettings.class).toString())
            .doesNotContain(value));
  }

  private static String randomKey(int bytes) {
    byte[] key = new byte[bytes];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  /** Every message of the failure chain, joined. */
  private static String rootMessages(Throwable failure) {
    StringBuilder all = new StringBuilder();
    for (Throwable cause : causes(failure)) {
      all.append(cause).append('\n');
    }
    return all.toString();
  }

  private static java.util.List<Throwable> causes(Throwable failure) {
    java.util.List<Throwable> chain = new java.util.ArrayList<>();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      chain.add(cause);
    }
    return chain;
  }

  /** Neither the value nor any 8-character window of it appears. */
  private static void assertNoFragmentOf(String value, String messages) {
    assertThat(messages).doesNotContain(value);
    for (int i = 0; i + 8 <= value.length(); i++) {
      assertThat(messages).doesNotContain(value.substring(i, i + 8));
    }
  }
}
