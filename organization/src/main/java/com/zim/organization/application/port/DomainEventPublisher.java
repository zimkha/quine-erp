package com.zim.organization.application.port;

import com.zim.shared.domain.DomainEvent;

import java.util.Collection;

@FunctionalInterface
public interface DomainEventPublisher {

    void publish(Collection<DomainEvent> domainEvents);
}
