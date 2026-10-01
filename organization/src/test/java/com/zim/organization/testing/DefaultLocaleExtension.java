package com.zim.organization.testing;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Locale;

/**
 * Saves the JVM default locale, with its FORMAT and DISPLAY categories,
 * before each test and restores all three after it, even when the test
 * fails. Tests may then change the default freely (surefire does not run
 * tests in parallel).
 */
public final class DefaultLocaleExtension
    implements BeforeEachCallback, AfterEachCallback {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(DefaultLocaleExtension.class);

  @Override
  public void beforeEach(ExtensionContext context) {
    context.getStore(NAMESPACE).put(
        SavedLocales.class,
        new SavedLocales(
            Locale.getDefault(),
            Locale.getDefault(Locale.Category.FORMAT),
            Locale.getDefault(Locale.Category.DISPLAY)
        )
    );
  }

  @Override
  public void afterEach(ExtensionContext context) {
    SavedLocales saved = context.getStore(NAMESPACE)
        .remove(SavedLocales.class, SavedLocales.class);
    if (saved == null) {
      return;
    }
    // setDefault(Locale) also resets the categories, so it goes first.
    Locale.setDefault(saved.defaultLocale());
    Locale.setDefault(Locale.Category.FORMAT, saved.format());
    Locale.setDefault(Locale.Category.DISPLAY, saved.display());
  }

  private record SavedLocales(
      Locale defaultLocale,
      Locale format,
      Locale display
  ) {
  }
}
