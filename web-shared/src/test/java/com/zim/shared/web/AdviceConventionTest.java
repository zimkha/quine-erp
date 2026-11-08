package com.zim.shared.web;

import com.zim.shared.domain.DomainException;
import com.zim.shared.web.testing.AdviceAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ordering and scoping conventions of the advices (T7b-0 AC5 to AC7).
 */
@WebMvcTest(AdviceConventionTest.PrecedenceController.class)
@Import({
    AdviceConventionTest.PrecedenceController.class,
    AdviceConventionTest.ModuleAdvice.class,
    WebSharedConfiguration.class
})
class AdviceConventionTest {

  @Autowired
  private MockMvc mockMvc;

  /** AC7: the module advice, ordered first, wins over the generic one. */
  @Test
  void shouldLetModuleAdviceWinForSubclassOfDomainException()
      throws Exception {
    mockMvc.perform(get("/api/test/precedence/own"))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.code").value("MODULE_OWN"));
  }

  @Test
  void shouldKeepGenericMappingForOtherDomainExceptions() throws Exception {
    mockMvc.perform(get("/api/test/precedence/other"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("OTHER"));
  }

  /** AC6: the generic advice is not scoped in any way. */
  @Test
  void shouldNotScopeTheGenericAdvice() {
    RestControllerAdvice advice = GenericExceptionAdvice.class
        .getAnnotation(RestControllerAdvice.class);

    assertThat(advice.basePackages()).isEmpty();
    assertThat(advice.basePackageClasses()).isEmpty();
    assertThat(advice.assignableTypes()).isEmpty();
    assertThat(advice.annotations()).isEmpty();
    assertThat(advice.value()).isEmpty();
  }

  @Test
  void shouldOrderGenericAdviceLastAndModulesBeforeIt() {
    assertThat(GenericExceptionAdvice.class.getAnnotation(Order.class).value())
        .isEqualTo(AdviceOrder.GENERIC);
    assertThat(AdviceOrder.MODULE).isLessThan(AdviceOrder.GENERIC);
  }

  /** AC5 on a context holding the generic and a well-formed module advice. */
  @Test
  void shouldAcceptOneMappingPerException() {
    try (var context = new AnnotationConfigApplicationContext(
        WebSharedConfiguration.class, ModuleAdvice.class)) {
      AdviceAssertions.assertOneMappingPerException(context);
    }
  }

  @Test
  void shouldRejectExceptionHandledByTwoAdvices() {
    try (var context = new AnnotationConfigApplicationContext(
        WebSharedConfiguration.class, DuplicatingAdvice.class)) {
      assertThatThrownBy(
          () -> AdviceAssertions.assertOneMappingPerException(context))
          .hasMessageContaining(DomainException.class.getName());
    }
  }

  @Test
  void shouldRejectModuleAdviceOrderedAfterTheGenericOne() {
    try (var context = new AnnotationConfigApplicationContext(
        WebSharedConfiguration.class, LateAdvice.class)) {
      assertThatThrownBy(
          () -> AdviceAssertions.assertOneMappingPerException(context))
          .hasMessageContaining("ordered before the generic advice");
    }
  }

  @Test
  void shouldRejectModuleAdviceHandlingForeignExceptionType() {
    try (var context = new AnnotationConfigApplicationContext(
        WebSharedConfiguration.class, ForeignAdvice.class)) {
      assertThatThrownBy(
          () -> AdviceAssertions.assertOneMappingPerException(context))
          .hasMessageContaining("belongs to another module");
    }
  }

  @Test
  void shouldRejectTwoGenericAdvices() {
    try (var context = new AnnotationConfigApplicationContext(
        WebSharedConfiguration.class)) {
      context.registerBean("secondGeneric", GenericExceptionAdvice.class);
      assertThatThrownBy(
          () -> AdviceAssertions.assertOneMappingPerException(context))
          .hasMessageContaining("exactly one bean");
    }
  }

  static final class OwnException extends DomainException {

    OwnException() {
      super("MODULE_OWN", "The module owns this exception");
    }
  }

  static final class OtherException extends DomainException {

    OtherException() {
      super("OTHER", "Not owned by the module advice");
    }
  }

  @RestController
  static class PrecedenceController {

    @GetMapping("/api/test/precedence/own")
    String own() {
      throw new OwnException();
    }

    @GetMapping("/api/test/precedence/other")
    String other() {
      throw new OtherException();
    }
  }

  @RestControllerAdvice
  @Order(AdviceOrder.MODULE)
  static class ModuleAdvice {

    @ExceptionHandler(OwnException.class)
    ResponseEntity<ApiErrorResponse> own(OwnException exception) {
      return GenericExceptionAdvice.error(
          HttpStatus.LOCKED, exception.code(), exception.getMessage());
    }
  }

  @RestControllerAdvice
  @Order(AdviceOrder.MODULE)
  static class DuplicatingAdvice {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiErrorResponse> domain(DomainException exception) {
      return GenericExceptionAdvice.error(
          HttpStatus.LOCKED, exception.code(), exception.getMessage());
    }
  }

  @RestControllerAdvice
  @Order(AdviceOrder.GENERIC)
  static class LateAdvice {

    @ExceptionHandler(OwnException.class)
    ResponseEntity<ApiErrorResponse> own(OwnException exception) {
      return GenericExceptionAdvice.error(
          HttpStatus.LOCKED, exception.code(), exception.getMessage());
    }
  }

  @RestControllerAdvice
  @Order(AdviceOrder.MODULE)
  static class ForeignAdvice {

    @ExceptionHandler(java.sql.SQLException.class)
    ResponseEntity<ApiErrorResponse> foreign(java.sql.SQLException exception) {
      return GenericExceptionAdvice.error(
          HttpStatus.LOCKED, "FOREIGN", "foreign");
    }
  }
}
