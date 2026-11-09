package com.logitrack.payment.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtContractValidator implements OAuth2TokenValidator<Jwt> {
  private static final Set<String> ROLES = Set.of("ADMIN", "CLIENTE", "REPARTIDOR");
  private final JwtProperties properties;

  JwtContractValidator(JwtProperties properties) {
    this.properties = properties;
  }

  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    try {
      UUID.fromString(jwt.getSubject());
      Object claim = jwt.getClaims().get("roles");
      boolean rolesValid =
          claim instanceof List<?> roles
              && !roles.isEmpty()
              && roles.stream().allMatch(role -> role instanceof String && ROLES.contains(role));
      if (rolesValid
          && properties.keyId().equals(jwt.getHeaders().get("kid"))
          && jwt.getAudience().contains(properties.audience())
          && jwt.getExpiresAt() != null
          && jwt.getIssuedAt() != null
          && jwt.getIssuedAt().isBefore(jwt.getExpiresAt())
          && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))) {
        return OAuth2TokenValidatorResult.success();
      }
    } catch (RuntimeException ignored) {
      // Claims ausentes o de tipo incorrecto también invalidan el token.
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "JWT fuera de contrato", null));
  }
}
