package com.zim.identity.domain.rule;

import com.zim.identity.domain.valueobject.Email;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

/** An e-mail is unique across the whole platform, whatever its case. */
public record EmailMustBeUniqueRule(
    Email candidate,
    boolean alreadyTaken
) implements BusinessRule {

  public EmailMustBeUniqueRule {
    Objects.requireNonNull(candidate, "Candidate e-mail cannot be null");
  }

  @Override
  public boolean isBroken() {
    return alreadyTaken;
  }

  @Override
  public String code() {
    return "EMAIL_ALREADY_EXISTS";
  }

  @Override
  public String message() {
    return "A user with this e-mail address already exists";
  }
}
