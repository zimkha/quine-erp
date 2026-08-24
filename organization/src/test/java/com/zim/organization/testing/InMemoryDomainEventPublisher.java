package com.zim.organization.testing;

import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.shared.domain.DomainEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public  final class InMemoryDomainEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> publishedEvents =
            new ArrayList<>();

    public void publish(Collection<DomainEvent> events) {
        publishedEvents.addAll(events);
    }

    public List<DomainEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

}
