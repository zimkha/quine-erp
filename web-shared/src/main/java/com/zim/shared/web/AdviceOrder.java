package com.zim.shared.web;

import org.springframework.core.Ordered;

/**
 * Order of the exception advices. The first advice that has a handler for
 * an exception wins, even if a later one has a more specific handler, so a
 * module advice must come before the generic one. Put the constant in
 * {@code @Order} on the advice class, not on a {@code @Bean} method.
 */
public final class AdviceOrder {

  /** The generic advice: last, so that module advices are tried first. */
  public static final int GENERIC = Ordered.LOWEST_PRECEDENCE;

  /** Every module advice. */
  public static final int MODULE = Ordered.LOWEST_PRECEDENCE - 100;

  private AdviceOrder() {
  }
}
