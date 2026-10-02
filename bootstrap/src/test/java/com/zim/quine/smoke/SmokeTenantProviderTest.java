package com.zim.quine.smoke;

import com.zim.organization.application.command.ActivateOrganizationCommand;
import com.zim.organization.application.handler.ActivateOrganizationHandler;
import com.zim.organization.application.handler.AddStoreHandler;
import com.zim.organization.application.handler.ChangeHeadquartersHandler;
import com.zim.organization.application.handler.CloseOrganizationHandler;
import com.zim.organization.application.handler.DeactivateStoreHandler;
import com.zim.organization.application.result.ActivateOrganizationResult;
import com.zim.organization.presentation.rest.TenantOrganizationController;
import com.zim.organization.presentation.rest.exception.ApiExceptionHandler;
import com.zim.quine.tenant.FailClosedTenantProvider;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the "smoke" profile the header provider replaces the fail-closed one:
 * both are imported, and the context only starts if exactly one is active.
 */
@WebMvcTest(
    controllers = TenantOrganizationController.class,
    properties = "spring.main.allow-bean-definition-overriding=false"
)
@ActiveProfiles("smoke")
@Import({
    TenantOrganizationController.class,
    ApiExceptionHandler.class,
    FailClosedTenantProvider.class,
    SmokeTenantProvider.class,
    SmokeController.class
})
class SmokeTenantProviderTest {

  @Autowired
  private MockMvc mockMvc;

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
  void shouldTakeTheTenantFromTheSmokeHeader() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID organization = UUID.randomUUID();
    when(activateOrganizationHandler.handle(any())).thenReturn(
        new ActivateOrganizationResult(
            organization, tenant, "ACTIVE", Instant.parse("2026-10-02T10:00:00Z")
        )
    );

    mockMvc.perform(
            post("/api/organizations/{id}/activation", organization)
                .header("X-Smoke-Tenant", tenant.toString())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    ArgumentCaptor<ActivateOrganizationCommand> command =
        ArgumentCaptor.forClass(ActivateOrganizationCommand.class);
    verify(activateOrganizationHandler).handle(command.capture());
    assertThat(command.getValue().tenantId()).isEqualTo(new TenantId(tenant));
  }

  @Test
  void shouldStayUnauthorizedWithoutOrWithMalformedHeader() throws Exception {
    mockMvc.perform(
            post("/api/organizations/{id}/activation", UUID.randomUUID())
        )
        .andExpect(status().isUnauthorized());
    mockMvc.perform(
            post("/api/organizations/{id}/activation", UUID.randomUUID())
                .header("X-Smoke-Tenant", "not-a-uuid")
        )
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TENANT_NOT_RESOLVED"));
  }
}
