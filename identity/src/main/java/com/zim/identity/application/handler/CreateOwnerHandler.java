package com.zim.identity.application.handler;

import com.zim.identity.application.command.CreateOwnerCommand;
import com.zim.identity.application.port.ClockProvider;
import com.zim.identity.application.port.PasswordHasher;
import com.zim.identity.application.port.UserIdGenerator;
import com.zim.identity.application.result.CreateOwnerResult;
import com.zim.identity.domain.model.User;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.rule.EmailMustBeUniqueRule;
import com.zim.identity.domain.rule.TenantMustHaveSingleOwnerRule;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.PlainPassword;
import com.zim.shared.domain.BusinessRule;
import com.zim.shared.domain.BusinessRuleViolationException;

import java.time.Instant;
import java.util.Objects;

/**
 * Creates the first user of a tenant. Meant to run inside the caller's
 * transaction, so that if it fails nothing else registered survives.
 */
public class CreateOwnerHandler {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final UserIdGenerator userIdGenerator;
  private final ClockProvider clockProvider;

  public CreateOwnerHandler(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      UserIdGenerator userIdGenerator,
      ClockProvider clockProvider
  ) {
    this.userRepository =
        Objects.requireNonNull(userRepository, "User repository cannot be null");
    this.passwordHasher =
        Objects.requireNonNull(passwordHasher, "Password hasher cannot be null");
    this.userIdGenerator = Objects.requireNonNull(
        userIdGenerator,
        "User id generator cannot be null"
    );
    this.clockProvider =
        Objects.requireNonNull(clockProvider, "Clock provider cannot be null");
  }

  public CreateOwnerResult handle(CreateOwnerCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    Email email = new Email(command.email());
    PlainPassword password = new PlainPassword(command.password());

    check(new EmailMustBeUniqueRule(email, userRepository.existsByEmail(email)));
    check(new TenantMustHaveSingleOwnerRule(
        userRepository.existsOwnerByTenantId(command.tenantId())
    ));

    PasswordHash passwordHash = passwordHasher.hash(password);
    Instant createdAt = Objects.requireNonNull(
        clockProvider.now(),
        "Current time cannot be null"
    );

    User owner = User.createOwner(
        userIdGenerator.generate(),
        command.tenantId(),
        email,
        passwordHash,
        createdAt
    );

    userRepository.save(owner);

    return new CreateOwnerResult(
        owner.id().value(),
        owner.tenantId().value(),
        owner.email().value(),
        owner.createdAt()
    );
  }

  private static void check(BusinessRule rule) {
    if (rule.isBroken()) {
      throw new BusinessRuleViolationException(rule);
    }
  }
}
