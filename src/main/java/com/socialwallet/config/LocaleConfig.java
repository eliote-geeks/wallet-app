package com.socialwallet.config;

import java.util.List;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfig {
  private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.FRENCH, Locale.ENGLISH);

  @Bean
  public LocaleResolver localeResolver() {
    return new QueryParamLocaleResolver(SUPPORTED_LOCALES, Locale.FRENCH);
  }

  static class QueryParamLocaleResolver extends AcceptHeaderLocaleResolver {
    private final List<Locale> supportedLocales;
    private final Locale defaultLocale;

    QueryParamLocaleResolver(List<Locale> supportedLocales, Locale defaultLocale) {
      this.supportedLocales = List.copyOf(supportedLocales);
      this.defaultLocale = defaultLocale;
      setSupportedLocales(this.supportedLocales);
      setDefaultLocale(defaultLocale);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
      Locale queryLocale = resolveFromQueryParam(request);
      if (queryLocale != null) {
        return queryLocale;
      }
      return super.resolveLocale(request);
    }

    private Locale resolveFromQueryParam(HttpServletRequest request) {
      String lang = request.getParameter("lang");
      if (lang == null || lang.isBlank()) {
        return null;
      }
      Locale candidate = Locale.forLanguageTag(lang.trim());
      Locale match = matchSupported(candidate);
      return match != null ? match : defaultLocale;
    }

    private Locale matchSupported(Locale candidate) {
      if (candidate == null) {
        return null;
      }
      for (Locale locale : supportedLocales) {
        if (locale.equals(candidate)) {
          return locale;
        }
      }
      for (Locale locale : supportedLocales) {
        if (locale.getLanguage().equalsIgnoreCase(candidate.getLanguage())) {
          return locale;
        }
      }
      return null;
    }
  }
}
