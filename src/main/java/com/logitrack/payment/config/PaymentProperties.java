package com.logitrack.payment.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("logitrack.payments")
public record PaymentProperties(
    boolean enabled,
    boolean sandbox,
    String accessToken,
    String webhookSecret,
    URI publicUrl,
    URI apiBaseUrl,
    URI shipmentUrl) {
  public boolean available() {
    return enabled
        && accessToken != null
        && !accessToken.isBlank()
        && webhookSecret != null
        && !webhookSecret.isBlank();
  }
}
