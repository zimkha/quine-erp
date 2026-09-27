package com.zim.shared.tenant;

import com.zim.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantNotResolvedExceptionTest {

  @Test
  void shouldExposeStableCode() {
    TenantNotResolvedException exception = new TenantNotResolvedException();

    assertThat(TenantNotResolvedException.CODE)
        .isEqualTo("TENANT_NOT_RESOLVED");
    assertThat(exception.code())
        .isEqualTo(TenantNotResolvedException.CODE);
  }

  @Test
  void shouldCarryGenericMessage() {
    assertThat(new TenantNotResolvedException())
        .hasMessage("The tenant of the current request could not be resolved")
        .hasNoCause();
  }

  @Test
  void shouldNotBeDomainException() {
    assertThat(new TenantNotResolvedException())
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(DomainException.class);
  }
}
