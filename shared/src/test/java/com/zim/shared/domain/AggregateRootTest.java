package com.zim.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRootTest {

    @Test
    void shouldRegisterAndPullDomainEvents() {
        TestAggregate aggregate = new TestAggregate();

        TestDomainEvent event = new TestDomainEvent(
                UUID.randomUUID(),
                Instant.parse("2026-08-02T12:00:00Z")
        );

        aggregate.performAction(event);

        assertThat(aggregate.domainEvents())
                .containsExactly(event);

        assertThat(aggregate.pullDomainEvents())
                .containsExactly(event);

        assertThat(aggregate.domainEvents())
                .isEmpty();
    }

    @Test
    void shouldThrowWhenBusinessRuleIsBroken() {
        TestAggregate aggregate = new TestAggregate();

        BusinessRule brokenRule = new BusinessRule() {
            @Override
            public boolean isBroken() {
                return true;
            }

            @Override
            public String code() {
                return "TEST_RULE_BROKEN";
            }

            @Override
            public String message() {
                return "Test rule is broken";
            }
        };

        assertThatThrownBy(() -> aggregate.verify(brokenRule))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(exception -> {
                    BusinessRuleViolationException domainException =
                            (BusinessRuleViolationException) exception;

                    assertThat(domainException.code())
                            .isEqualTo("TEST_RULE_BROKEN");

                    assertThat(domainException.getMessage())
                            .isEqualTo("Test rule is broken");
                });
    }

    private static final class TestAggregate extends AggregateRoot {

        void performAction(DomainEvent event) {
            registerEvent(event);
        }

        void verify(BusinessRule rule) {
            checkRule(rule);
        }
    }

    private record TestDomainEvent(
            UUID eventId,
            Instant occurredAt
    ) implements DomainEvent {

        @Override
        public String eventType() {
            return "test.event.v1";
        }
    }
}
