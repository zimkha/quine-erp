package com.zim.identity.application.handler;

import com.zim.identity.application.command.LoginCommand;
import com.zim.identity.application.exception.InvalidCredentialsException;
import com.zim.identity.application.port.AccessTokenIssuer;
import com.zim.identity.application.port.ClockProvider;
import com.zim.identity.application.port.PasswordHasher;
import com.zim.identity.application.port.PasswordVerifier;
import com.zim.identity.application.result.LoginResult;
import com.zim.identity.domain.exception.InvalidValueException;
import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordAttempt;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks credentials and issues an access token.
 *
 * <p>Every attempt performs exactly one password verification, whether the
 * e-mail is malformed, unknown or known: an unknown or malformed e-mail is
 * verified against a dummy hash made at construction by the real hasher, so
 * the cost follows the real one. The residual difference (the database
 * lookup) is accepted. All failures end in the same
 * {@link InvalidCredentialsException}.
 */
public class LoginHandler {

  private final UserRepository userRepository;
  private final PasswordVerifier passwordVerifier;
  private final AccessTokenIssuer accessTokenIssuer;
  private final ClockProvider clockProvider;
  private final PasswordHash dummyHash;

  public LoginHandler(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      PasswordVerifier passwordVerifier,
      AccessTokenIssuer accessTokenIssuer,
      ClockProvider clockProvider
  ) {
    this.userRepository =
        Objects.requireNonNull(userRepository, "User repository cannot be null");
    Objects.requireNonNull(passwordHasher, "Password hasher cannot be null");
    this.passwordVerifier =
        Objects.requireNonNull(passwordVerifier, "Password verifier cannot be null");
    this.accessTokenIssuer = Objects.requireNonNull(
        accessTokenIssuer,
        "Access token issuer cannot be null"
    );
    this.clockProvider =
        Objects.requireNonNull(clockProvider, "Clock provider cannot be null");
    // A random value of a valid length: the hash must be well formed, or a
    // verifier would answer false at once, without any hashing work.
    this.dummyHash = Objects.requireNonNull(
        passwordHasher.hash(new PlainPassword(UUID.randomUUID().toString())),
        "Dummy hash cannot be null"
    );
  }

  public LoginResult handle(LoginCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    Optional<User> user = Email.parse(command.email()).flatMap(this::find);

    PasswordHash hash = user.map(User::passwordHash).orElse(dummyHash);
    boolean matches = passwordVerifier.matches(
        new PasswordAttempt(command.password()),
        hash
    );

    if (user.isEmpty() || !matches) {
      throw new InvalidCredentialsException();
    }

    Instant now = Objects.requireNonNull(
        clockProvider.now(),
        "Current time cannot be null"
    );
    return accessTokenIssuer.issue(
        user.get().id(),
        user.get().tenantId(),
        now
    );
  }

  private Optional<User> find(Email email) {
    try {
      return userRepository.findByEmail(email);
    } catch (InvalidValueException exception) {
      // Defensive: a stored row that no longer satisfies the value objects
      // is a failed login, not a 422.
      return Optional.empty();
    }
  }
}
