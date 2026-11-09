package com.logitrack.payment.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtContractValidatorTest {
  @Test
  void requiresPaymentAudienceAndKnownKeyAndRoles() {
    var validator =
        new JwtContractValidator(
            new JwtProperties(
                new ClassPathResource("unused.pem"),
                "key-1",
                "https://security-service",
                "payment-service"));
    for (String audience : List.of("shipment-service", "payment-service")) {
      var jwt =
          Jwt.withTokenValue("test")
              .header("kid", "key-1")
              .subject(UUID.randomUUID().toString())
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim("aud", List.of(audience))
              .claim("roles", List.of("CLIENTE"))
              .build();
      assertThat(validator.validate(jwt).hasErrors())
          .isEqualTo(!audience.equals("payment-service"));
    }
    var invalid =
        Jwt.withTokenValue("test")
            .header("kid", "wrong-key")
            .subject(UUID.randomUUID().toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("aud", List.of("payment-service"))
            .claim("roles", List.of("OWNER"))
            .build();
    assertThat(validator.validate(invalid).hasErrors()).isTrue();
  }
}
