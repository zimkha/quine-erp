package com.zim.quine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * T7b-AC6 on the real application: it refuses to start without a usable
 * QUINE_JWT_SECRET, and the failure names the variable. (The no-value
 * guarantee is tested per case in TenantJwtConfigurationTest, identity.)
 */
class QuineApplicationWithoutSecretIT {

  @Test
  void shouldRefuseToStartWhenTheVariableIsNotSet() {
    assumeThat(System.getenv("QUINE_JWT_SECRET")).isNull();

    assertThat(failureOfStartWith()).contains("QUINE_JWT_SECRET");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "not base64 !!", "dG9vLXNob3J0"})
  void shouldRefuseToStartWithAnUnusableValue(String value) {
    assertThat(failureOfStartWith("quine.security.tenant.jwt.secret=" + value))
        .contains("QUINE_JWT_SECRET");
  }

  private static String failureOfStartWith(String... extra) {
    List<String> properties = new ArrayList<>(List.of(
        "spring.datasource.url=" + PostgresContainerSupport.POSTGRES.getJdbcUrl(),
        "spring.datasource.username=" + PostgresContainerSupport.POSTGRES.getUsername(),
        "spring.datasource.password=" + PostgresContainerSupport.POSTGRES.getPassword(),
        "server.port=0"
    ));
    properties.addAll(List.of(extra));

    Throwable[] failure = new Throwable[1];
    assertThatThrownBy(() -> {
      try (var context = new SpringApplicationBuilder(QuineApplication.class)
          .web(WebApplicationType.NONE)
          .run(properties.stream().map(p -> "--" + p).toArray(String[]::new))) {
        failure[0] = null;
      }
    }).satisfies(thrown -> failure[0] = thrown);

    StringBuilder messages = new StringBuilder();
    for (Throwable cause = failure[0]; cause != null; cause = cause.getCause()) {
      messages.append(cause).append('\n');
    }
    return messages.toString();
  }
}
