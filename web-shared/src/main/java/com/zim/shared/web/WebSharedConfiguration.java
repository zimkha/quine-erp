package com.zim.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The one way to wire the generic exception advice. Import it once in the
 * application and in every web slice or integration test that imports a
 * controller. It is deliberately not an auto-configuration: a slice that
 * forgets it must fail loudly, not silently lose the error contract. Do not
 * also declare {@link GenericExceptionAdvice} with {@code @Bean} or scan it.
 */
@Configuration(proxyBeanMethods = false)
@Import(GenericExceptionAdvice.class)
public class WebSharedConfiguration {
}
