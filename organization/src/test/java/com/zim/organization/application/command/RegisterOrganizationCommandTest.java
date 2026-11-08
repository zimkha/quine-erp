package com.zim.organization.application.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterOrganizationCommandTest {

  // Fictitious test value of the required length.
  private static final String PASSWORD = "not-a-real-password-1";

  @Test
  void shouldNeverPrintTheOwnerPassword() {
    RegisterOrganizationCommand command = new RegisterOrganizationCommand(
        "Quincaillerie Thiès",
        "Quincaillerie Thiès SARL",
        "XOF",
        "THIES-01",
        "Magasin principal",
        "owner@example.com",
        PASSWORD
    );

    assertThat(command.toString())
        .doesNotContain(PASSWORD)
        .contains("[PROTECTED]")
        .contains("owner@example.com");
  }
}
