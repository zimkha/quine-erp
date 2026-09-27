package com.zim.organization.infrastructure.event;

import com.zim.organization.application.port.DomainEventPublisher;
import com.zim.shared.domain.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.Objects;

/**
 * Publishes domain events as Spring application events, synchronously, on
 * the caller's thread and inside the command's transaction.
 *
 * <p>Consumer contract:
 * <ul>
 *   <li>Use {@code @TransactionalEventListener(phase = AFTER_COMMIT)} for
 *   anything that must only see committed changes. Those listeners are not
 *   invoked if the command rolls back.</li>
 *   <li>A plain {@code @EventListener} runs inside the transaction, before
 *   commit. An exception it throws rolls the whole command back.</li>
 * </ul>
 */
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