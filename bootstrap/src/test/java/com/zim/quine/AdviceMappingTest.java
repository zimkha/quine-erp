package com.zim.quine;

import com.zim.shared.web.AdviceOrder;
import com.zim.shared.web.GenericExceptionAdvice;
import com.zim.shared.web.WebSharedConfiguration;
import com.zim.shared.web.testing.AdviceAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7b-0 AC5 on the application: every advice class on the classpath of the
 * composition root is imported by {@link QuineApplication}, the generic one
 * is a single bean, and no exception type is mapped by two advices.
 */
class AdviceMappingTest {

  @Test
  void shouldMapEachExceptionOnceAcrossEveryAdviceOfTheApplication() {
    List<Class<?>> advices = adviceClasses();
    assertThat(advices).contains(GenericExceptionAdvice.class);

    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(WebSharedConfiguration.class);
      advices.stream()
          .filter(type -> type != GenericExceptionAdvice.class)
          .forEach(context::register);
      context.refresh();

      AdviceAssertions.assertOneMappingPerException(context);
    }
  }

  @Test
  void shouldImportEveryAdviceInTheApplication() {
    List<Class<?>> imported = Arrays.asList(
        QuineApplication.class.getAnnotation(Import.class).value());

    assertThat(imported).contains(WebSharedConfiguration.class);
    assertThat(adviceClasses())
        .filteredOn(type -> type != GenericExceptionAdvice.class)
        .allSatisfy(advice -> {
          assertThat(imported).contains(advice);
          assertThat(advice.getAnnotation(
              org.springframework.core.annotation.Order.class).value())
              .isEqualTo(AdviceOrder.MODULE);
        });
  }

  private static List<Class<?>> adviceClasses() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));

    return scanner.findCandidateComponents("com.zim").stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(name -> ClassUtils.resolveClassName(
            name, AdviceMappingTest.class.getClassLoader()))
        .filter(type -> !type.getName().startsWith("com.zim.shared.web.")
            || type == GenericExceptionAdvice.class)
        .toList();
  }
}
