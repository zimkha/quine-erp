package com.zim.shared.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents =
            new ArrayList<>();

    protected final void registerEvent(
            DomainEvent domainEvent
    ) {
        domainEvents.add(
                Objects.requireNonNull(
                        domainEvent,
                        "Domain event cannot be null"
                )
        );
    }

    protected final void checkRule(
            BusinessRule rule
    ) {
        Objects.requireNonNull(
                rule,
                "Business rule cannot be null"
        );

        if (rule.isBroken()) {
            throw new BusinessRuleViolationException(rule);
        }
    }

    public final List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public final List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events =
                List.copyOf(domainEvents);

        domainEvents.clear();

        return events;
    }

    public final void clearDomainEvents() {
        domainEvents.clear();
    }
}