package com.zim.quine.tenant;

import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.handler.CloseOrganizationHandler;
import com.zim.organization.application.handler.DeactivateStoreHandler;
import com.zim.organization.presentation.rest.TenantOrganizationController;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without the "smoke" profile, no tenant can be resolved: always 401. */
@WebMvcTest(TenantOrganizationController.class)
@Import({
    TenantOrganizationController.class,
    ApiExceptionHandler.class,
    FailClosedTenantProvider.class
})
class FailClosedTenantProviderTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private FailClosedTenantProvider provider;

  @MockitoBean
  private AddStoreHandler addStoreHandler;

  @MockitoBean
  private ChangeHeadquartersHandler changeHeadquartersHandler;

  @MockitoBean
  private DeactivateStoreHandler deactivateStoreHandler;

  @MockitoBean
  private CloseOrganizationHandler closeOrganizationHandler;

  @MockitoBean
  private ActivateOrganizationHandler activateOrganizationHandler;

  @Test
  void shouldNeverResolveATenant() {
    assertThatThrownBy(provider::currentTenant)
        .isInstanceOf(TenantNotResolvedException.class);
  }

  @Test
  void shouldAnswerUnauthorizedOnTenantEndpoints() throws Exception {
    mockMvc.perform(
            post("/api/organizations/{id}/activation", UUID.randomUUID())
                .header("X-Smoke-Tenant", UUID.randomUUID().toString())
        )
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"quine-erp\""
        ))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"));

    verifyNoInteractions(activateOrganizationHandler);
  }
}
