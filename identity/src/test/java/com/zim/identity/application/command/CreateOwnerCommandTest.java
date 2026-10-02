package com.zim.identity.application.command;

import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOwnerCommandTest {

  @Test
  void shouldNeverPrintThePassword() {
    CreateOwnerCommand command = new CreateOwnerCommand(
        TenantId.generate(), "a@b.com", "S3cret-Passw0rd!"
    );

    assertThat(command.toString()).doesNotContain("S3cret-Passw0rd!");
    assertThat(command.toString()).contains("[PROTECTED]");
  }
}
