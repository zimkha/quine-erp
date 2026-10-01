package com.zim.organization.testing;

import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A JVM default locale plus an optional {@code Accept-Language} header.
 * Under MockMvc the request locale is {@code en} unless the header is set,
 * so the header is what exposes locale-dependent messages; the JVM default
 * covers number formatting and the real-container fallback. Use it with
 * {@link DefaultLocaleExtension}, which restores the default afterwards.
 */
public record LocaleScenario(Locale jvmDefault, String acceptLanguage) {

  private static final List<LocaleScenario> NON_ENGLISH = List.of(
      new LocaleScenario(Locale.FRENCH, null),
      new LocaleScenario(Locale.FRENCH, "fr"),
      new LocaleScenario(Locale.FRENCH, "de"),
      new LocaleScenario(Locale.GERMAN, null),
      new LocaleScenario(Locale.GERMAN, "de"),
      new LocaleScenario(Locale.GERMAN, "fr")
  );

  /** Arabic (Egypt) formats numbers with Arabic-Indic digits. */
  public static final Locale ARABIC_EGYPT = Locale.forLanguageTag("ar-EG");

  public static List<LocaleScenario> nonEnglish() {
    return NON_ENGLISH;
  }

  /**
   * Every case crossed with every non-English scenario; the scenario comes
   * first in each resulting argument list.
   */
  public static Stream<Arguments> crossNonEnglish(Stream<Arguments> cases) {
    List<Arguments> caseList = cases.toList();
    return NON_ENGLISH.stream().flatMap(scenario -> caseList.stream()
        .map(testCase -> {
          Object[] values = testCase.get();
          Object[] withScenario = new Object[values.length + 1];
          withScenario[0] = scenario;
          System.arraycopy(values, 0, withScenario, 1, values.length);
          return Arguments.of(withScenario);
        }));
  }

  /** Sets the JVM default locale (all categories). */
  public void activate() {
    Locale.setDefault(jvmDefault);
  }

  public MockHttpServletRequestBuilder applyTo(
      MockHttpServletRequestBuilder request
  ) {
    return acceptLanguage == null
        ? request
        : request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
  }

  @Override
  public String toString() {
    String header = acceptLanguage == null ? "none" : acceptLanguage;
    return "default=" + jvmDefault.toLanguageTag()
        + ", Accept-Language=" + header;
  }
}
