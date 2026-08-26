package com.zim.organization.infrastructure.event;

import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.shared.domain.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.Objects;

public final class SpringDomainEventPublisher
        implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(
            ApplicationEventPublisher publisher
    ) {
        this.publisher = Objects.requireNonNull(
                publisher,
                "ApplicationEventPublisher cannot be null"
        );
    }

    @Override
    public void publish(
            Collection<DomainEvent> events
    ) {
        Objects.requireNonNull(
                events,
                "Domain events cannot be null"
        );

        events.forEach(
                publisher::publishEvent
        );
    }
}