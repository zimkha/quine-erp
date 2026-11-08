package com.zim.identity.application.handler;

import com.zim.identity.application.command.LoginCommand;
import com.zim.identity.application.exception.InvalidCredentialsException;
import com.zim.identity.application.port.PasswordVerifier;
import com.zim.identity.application.result.LoginResult;
import com.zim.identity.domain.exception.InvalidValueException;
import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordAttempt;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.identity.infrastructure.security.BcryptPasswordHasher;
import com.zim.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
  private static final String PASSWORD = "not-a-real-password-1";

  private final BcryptPasswordHasher bcrypt = new BcryptPasswordHasher();
  private final CountingVerifier verifier = new CountingVerifier(bcrypt);
  private final List<Object[]> issued = new ArrayList<>();
  private final InMemoryUsers users = new InMemoryUsers();

  private TenantId tenant;
  private UserId userId;
  private LoginHandler handler;

  @BeforeEach
  void setUp() {
    tenant = TenantId.generate();
    userId = UserId.generate();
    users.stored = User.createOwner(
        userId, tenant, new Email("awa@example.com"),
        bcrypt.hash(new com.zim.identity.domain.valueobject.PlainPassword(PASSWORD)),
        NOW
    );
    handler = new LoginHandler(
        users,
        bcrypt,
        verifier,
        (user, tenantId, at) -> {
          issued.add(new Object[] {user, tenantId, at});
          return new LoginResult("a.token.value", "Bearer", 1800);
        },
        () -> NOW
    );
  }

  @Test
  void shouldIssueATokenForTheRightCredentials() {
    LoginResult result = handler.handle(
        new LoginCommand("  Awa@Example.COM ", PASSWORD)
    );

    assertThat(result.accessToken()).isEqualTo("a.token.value");
    assertThat(issued).singleElement().satisfies(call -> {
      assertThat(call[0]).isEqualTo(userId);
      assertThat(call[1]).isEqualTo(tenant);
      assertThat(call[2]).isEqualTo(NOW);
    });
    assertThat(verifier.calls).isEqualTo(1);
  }

  @Test
  void shouldFailAndVerifyOnceForAWrongPassword() {
    assertFails(new LoginCommand("awa@example.com", "wrong-fake-password-9"));

    assertThat(verifier.calls).isEqualTo(1);
    assertThat(verifier.hashes.getFirst())
        .isEqualTo(users.stored.passwordHash());
    assertThat(issued).isEmpty();
  }

  @Test
  void shouldFailAndVerifyOnceAgainstTheDummyHashForAnUnknownEmail() {
    assertFails(new LoginCommand("nobody@example.com", PASSWORD));

    assertThat(verifier.calls).isEqualTo(1);
    assertThat(verifier.hashes.getFirst())
        .isNotEqualTo(users.stored.passwordHash());
    assertThat(issued).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "not-an-email", "a@b", "é@b.com"})
  void shouldFailAndVerifyOnceForAMalformedEmail(String email) {
    assertFails(new LoginCommand(email, PASSWORD));

    assertThat(verifier.calls).isEqualTo(1);
    assertThat(users.lookups).isZero();
    assertThat(issued).isEmpty();
  }

  @Test
  void shouldFailWhenTheVerifierRefusesAnOver72BytePassword() {
    // The real verifier: 72 matching bytes plus one more must not log in.
    String exactly72 = "a".repeat(72);
    users.stored = User.createOwner(
        userId, tenant, new Email("awa@example.com"),
        bcrypt.hash(new com.zim.identity.domain.valueobject.PlainPassword(exactly72)),
        NOW
    );

    assertFails(new LoginCommand("awa@example.com", exactly72 + "b"));
    assertThat(handler.handle(new LoginCommand("awa@example.com", exactly72)))
        .isNotNull();
  }

  @Test
  void shouldAcceptAShortPasswordAttemptAsAPlainFailureNotAValidationError() {
    assertThatThrownBy(() -> handler.handle(
        new LoginCommand("awa@example.com", "x")
    )).isExactlyInstanceOf(InvalidCredentialsException.class);
    assertThat(verifier.calls).isEqualTo(1);
  }

  @Test
  void shouldTreatAStoredRowThatFailsTheEmailTypeAsAFailedLogin() {
    users.failOnFind = true;

    assertFails(new LoginCommand("awa@example.com", PASSWORD));

    assertThat(verifier.calls).isEqualTo(1);
  }

  @Test
  void shouldHashTheDummyWithTheRealHasherSoItIsWellFormed() {
    assertFails(new LoginCommand("nobody@example.com", PASSWORD));

    String dummy = verifier.hashes.getFirst().value();
    assertThat(dummy).startsWith("$2").hasSize(60);
    // Well formed means: a verification against it does real work, which a
    // malformed hash would skip. It matches no password we know.
    assertThat(bcrypt.matches(new PasswordAttempt(PASSWORD),
        verifier.hashes.getFirst())).isFalse();
  }

  @Test
  void shouldNeverCarryTheEmailOrACauseInTheException() {
    Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
        () -> handler.handle(new LoginCommand("nobody@example.com", PASSWORD))
    );

    assertThat(thrown).isExactlyInstanceOf(InvalidCredentialsException.class);
    assertThat(thrown).hasNoCause();
    assertThat(thrown.getMessage())
        .doesNotContain("nobody").doesNotContain(PASSWORD);
    assertThat(thrown).isNotInstanceOf(
        com.zim.shared.domain.DomainException.class
    );
  }

  private void assertFails(LoginCommand command) {
    assertThatThrownBy(() -> handler.handle(command))
        .isExactlyInstanceOf(InvalidCredentialsException.class);
  }

  /** Counts verifications, delegating to the real BCrypt. */
  private static final class CountingVerifier implements PasswordVerifier {

    private final PasswordVerifier delegate;
    int calls;
    final List<PasswordHash> hashes = new ArrayList<>();

    CountingVerifier(PasswordVerifier delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean matches(PasswordAttempt attempt, PasswordHash stored) {
      calls++;
      hashes.add(stored);
      return delegate.matches(attempt, stored);
    }
  }

  private static final class InMemoryUsers implements UserRepository {

    User stored;
    boolean failOnFind;
    int lookups;

    @Override
    public void save(User user) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsByEmail(Email email) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsOwnerByTenantId(TenantId tenantId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
      lookups++;
      if (failOnFind) {
        throw new InvalidValueException("INVALID_EMAIL", "stored row is bad");
      }
      return Optional.ofNullable(stored)
          .filter(user -> user.email().equals(email));
    }
  }
}
