package com.logitrack.payment.security;

import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
  @Bean
  JwtDecoder jwtDecoder(JwtProperties p) throws IOException {
    try (var stream = p.publicKeyLocation().getInputStream()) {
      var decoder =
          NimbusJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(stream))
              .signatureAlgorithm(SignatureAlgorithm.RS256)
              .build();
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer(p.issuer()), new JwtContractValidator(p)));
      return decoder;
    }
  }

  @Bean
  @org.springframework.core.annotation.Order(2)
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/mercadopago")
                .permitAll()
                .requestMatchers("/api/v1/payments/**")
                .hasAnyRole("ADMIN", "CLIENTE")
                .anyRequest()
                .denyAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
