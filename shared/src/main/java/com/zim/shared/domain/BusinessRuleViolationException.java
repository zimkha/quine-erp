package com.zim.shared.domain;

import java.util.Objects;

public final class BusinessRuleViolationException
        extends DomainException {

    private final String ruleName;

    public BusinessRuleViolationException(BusinessRule rule) {
        super(
                Objects.requireNonNull(rule, "Business rule cannot be null")
                        .code(),
                rule.message()
        );

        this.ruleName = rule.getClass().getSimpleName();
    }

    public String ruleName() {
        return ruleName;
    }
}
