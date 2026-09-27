package com.zim.organization.application.port;

import com.zim.shared.domain.DomainEvent;

import java.util.Collection;

/**
 * Publishes the domain events pulled from an aggregate.
 *
 * <p>Handlers call this after saving the aggregate, inside the same
 * transaction as the load/modify/save. Consumers that must only react to
 * committed state (other modules, notifications, read models) therefore
 * need to run after commit; consumers running inside the transaction take
 * part in it, and a failure there rolls the command back.
 *
 * <p>Delivery is best-effort: there is no outbox, so events are lost if the
 * process stops between commit and dispatch.
 */
@FunctionalInterface
public interface DomainEventPublisher {

  void publish(Collection<DomainEvent> domainEvents);
}
