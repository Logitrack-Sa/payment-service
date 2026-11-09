package com.logitrack.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ManagementSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain managementSecurity(
      HttpSecurity http, @Value("${management.server.port:18085}") int port) throws Exception {
    return http.securityMatcher(request -> request.getLocalPort() == port)
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/prometheus")
                .permitAll()
                .anyRequest()
                .denyAll())
        .build();
  }
}
