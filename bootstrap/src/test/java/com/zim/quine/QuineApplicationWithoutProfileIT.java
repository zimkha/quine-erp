package com.zim.quine;

import com.zim.quine.tenant.FailClosedTenantProvider;
import com.zim.shared.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** With no profile the app must start, and the only provider fails closed. */
@SpringBootTest(classes = QuineApplication.class)
class QuineApplicationWithoutProfileIT {

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PostgresContainerSupport.POSTGRES::getPassword);
  }

  @Autowired
  CurrentTenantProvider tenantProvider;

  @Test
  void shouldStartWithTheFailClosedProvider() {
    assertThat(tenantProvider).isInstanceOf(FailClosedTenantProvider.class);
  }
}
