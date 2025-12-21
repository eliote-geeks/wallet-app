package com.socialwallet.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class JwtConfig {
  @Bean
  public JwtDecoder jwtDecoder(AppSecurityProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
      new JwtTimestampValidator(),
      new AllowedIssuerValidator(properties.getAllowedIssuers())
    );
    decoder.setJwtValidator(validator);
    return decoder;
  }

  static class AllowedIssuerValidator implements OAuth2TokenValidator<Jwt> {
    private final Set<String> allowedIssuers;

    AllowedIssuerValidator(List<String> allowedIssuers) {
      this.allowedIssuers = allowedIssuers.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
      if (token.getIssuer() == null) {
        return failure("missing_issuer", "Token issuer is missing");
      }
      String issuer = token.getIssuer().toString().trim().toLowerCase(Locale.ROOT);
      if (allowedIssuers.contains(issuer)) {
        return OAuth2TokenValidatorResult.success();
      }
      return failure("invalid_issuer", "Token issuer is not allowed");
    }

    private OAuth2TokenValidatorResult failure(String code, String description) {
      return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
  }
}
