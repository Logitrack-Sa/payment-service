package com.logitrack.payment.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("logitrack.security")
public record JwtProperties(
    @NotNull Resource publicKeyLocation,
    @NotBlank String keyId,
    @NotBlank String issuer,
    @NotBlank String audience) {}
