package com.zim.identity.application.handler;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.result.CreateOwnerResult;
import com.zim.identity.domain.exception.InvalidValueException;
import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.shared.domain.BusinessRuleViolationException;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateOwnerHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
  private static final String PASSWORD = "S3cret-Passw0rd!";

  private final InMemoryUsers users = new InMemoryUsers();
  private CreateOwnerHandler handler;
  private TenantId tenant;

  @BeforeEach
  void setUp() {
    tenant = TenantId.generate();
    handler = new CreateOwnerHandler(
        users,
        password -> new PasswordHash("hashed:" + password.reveal().length()),
        () -> new UserId(UUID.randomUUID()),
        () -> NOW
    );
  }

  @Test
  void shouldCreateAndSaveTheOwner() {
    CreateOwnerResult result = handler.handle(
        new CreateOwnerCommand(tenant, "  Awa@Example.com ", PASSWORD)
    );

    assertThat(result.tenantId()).isEqualTo(tenant.value());
    assertThat(result.email()).isEqualTo("awa@example.com");
    assertThat(result.createdAt()).isEqualTo(NOW);
    assertThat(users.saved).hasSize(1);
    assertThat(users.saved.get(0).isOwner()).isTrue();
  }

  @Test
  void shouldStoreAHashAndNeverThePassword() {
    handler.handle(new CreateOwnerCommand(tenant, "a@b.com", PASSWORD));

    User saved = users.saved.get(0);
    assertThat(saved.passwordHash().value()).doesNotContain(PASSWORD);
  }

  @Test
  void shouldRejectAnEmailAlreadyUsedWhateverItsCase() {
    handler.handle(new CreateOwnerCommand(tenant, "a@b.com", PASSWORD));

    assertThatThrownBy(() -> handler.handle(
        new CreateOwnerCommand(TenantId.generate(), "A@B.COM", PASSWORD)
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("EMAIL_ALREADY_EXISTS");
    assertThat(users.saved).hasSize(1);
  }

  @Test
  void shouldRejectASecondOwnerForTheSameTenant() {
    handler.handle(new CreateOwnerCommand(tenant, "a@b.com", PASSWORD));

    assertThatThrownBy(() -> handler.handle(
        new CreateOwnerCommand(tenant, "other@b.com", PASSWORD)
    ))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("TENANT_ALREADY_HAS_OWNER");
    assertThat(users.saved).hasSize(1);
  }

  @Test
  void shouldSaveNothingWhenTheEmailIsInvalid() {
    assertThatThrownBy(() -> handler.handle(
        new CreateOwnerCommand(tenant, "not-an-email", PASSWORD)
    )).isInstanceOf(InvalidValueException.class);
    assertThat(users.saved).isEmpty();
  }

  @Test
  void shouldSaveNothingWhenThePasswordIsTooShort() {
    assertThatThrownBy(() -> handler.handle(
        new CreateOwnerCommand(tenant, "a@b.com", "short")
    )).isInstanceOf(InvalidValueException.class);
    assertThat(users.saved).isEmpty();
  }

  @Test
  void shouldNeverLeakThePasswordInAnError() {
    Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
        () -> handler.handle(new CreateOwnerCommand(tenant, "bad", PASSWORD))
    );

    assertThat(thrown).isNotNull();
    assertThat(String.valueOf(thrown.getMessage())).doesNotContain(PASSWORD);
  }

  private static final class InMemoryUsers implements UserRepository {

    final List<User> saved = new ArrayList<>();

    @Override
    public void save(User user) {
      saved.add(user);
    }

    @Override
    public boolean existsByEmail(Email email) {
      return saved.stream().anyMatch(user -> user.email().equals(email));
    }

    @Override
    public boolean existsOwnerByTenantId(TenantId tenantId) {
      return saved.stream().anyMatch(
          user -> user.isOwner() && user.tenantId().equals(tenantId)
      );
    }

    @Override
    public Optional<User> findByEmail(Email email) {
      return saved.stream().filter(user -> user.email().equals(email)).findFirst();
    }
  }
}
