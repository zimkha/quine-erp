package com.zim.organization.presentation.rest.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterOrganizationRequestTest {

  // Fictitious test value of the required length.
  private static final String PASSWORD = "not-a-real-password-1";

  @Test
  void shouldNeverPrintTheOwnerPassword() {
    RegisterOrganizationRequest request = new RegisterOrganizationRequest(
        "Quincaillerie Thiès",
        "Quincaillerie Thiès SARL",
        "XOF",
        "THIES-01",
        "Magasin principal",
        "owner@example.com",
        PASSWORD
    );

    assertThat(request.toString()).doesNotContain(PASSWORD);
  }
}
