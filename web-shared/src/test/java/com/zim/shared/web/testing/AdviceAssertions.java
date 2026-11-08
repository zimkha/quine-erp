package com.zim.shared.web.testing;

import com.zim.shared.web.AdviceOrder;
import com.zim.shared.web.GenericExceptionAdvice;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.ControllerAdviceBean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks of the one-mapping-per-exception convention (T7b-0 AC5) on a
 * context: exactly one generic advice, no exception type handled by two
 * advices, every module advice ordered before the generic one and handling
 * only exceptions of its own module (the first three package segments of
 * the advice class, e.g. {@code com.zim.organization}).
 */
public final class AdviceAssertions {

  private AdviceAssertions() {
  }

  public static void assertOneMappingPerException(ApplicationContext context) {
    List<ControllerAdviceBean> advices =
        ControllerAdviceBean.findAnnotatedBeans(context);

    List<ControllerAdviceBean> generic = advices.stream()
        .filter(advice -> advice.getBeanType() == GenericExceptionAdvice.class)
        .toList();
    assertThat(generic)
        .as("the generic advice must be exactly one bean")
        .hasSize(1);
    assertThat(generic.getFirst().getOrder())
        .isEqualTo(AdviceOrder.GENERIC);

    Map<Class<?>, Class<?>> handledBy = new HashMap<>();
    List<String> clashes = new ArrayList<>();
    for (ControllerAdviceBean advice : advices) {
      Class<?> type = advice.getBeanType();
      assertThat(type).isNotNull();
      boolean isGeneric = type == GenericExceptionAdvice.class;
      if (!isGeneric) {
        assertThat(advice.getOrder())
            .as("%s must be ordered before the generic advice",
                type.getName())
            .isLessThan(generic.getFirst().getOrder());
      }
      for (Class<?> exception : handledExceptions(type)) {
        Class<?> previous = handledBy.put(exception, type);
        if (previous != null) {
          clashes.add(exception.getName() + " is handled by both "
              + previous.getName() + " and " + type.getName());
        }
        if (!isGeneric) {
          assertThat(exception.getName())
              .as("%s handles %s, which belongs to another module",
                  type.getName(), exception.getName())
              .startsWith(moduleRoot(type) + ".");
        }
      }
    }
    assertThat(clashes).as("exception types mapped twice").isEmpty();
  }

  private static List<Class<?>> handledExceptions(Class<?> advice) {
    List<Class<?>> handled = new ArrayList<>();
    for (Method method : advice.getDeclaredMethods()) {
      ExceptionHandler handler = AnnotatedElementUtils
          .findMergedAnnotation(method, ExceptionHandler.class);
      if (handler != null) {
        handled.addAll(List.of(handler.value()));
      }
    }
    return handled;
  }

  private static String moduleRoot(Class<?> advice) {
    String[] segments = advice.getPackageName().split("\\.");
    return String.join(".", List.of(segments).subList(0, 3));
  }
}
