package com.logitrack.payment.service;

import static org.assertj.core.api.Assertions.*;

import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.config.PaymentProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {
  WebhookVerifier verifier(boolean enabled) {
    return new WebhookVerifier(
        new PaymentProperties(
            enabled,
            true,
            "TEST-token",
            "test-only-secret",
            URI.create("https://example.test"),
            URI.create("https://api.mercadopago.com"),
            URI.create("http://shipment-service")));
  }

  @Test
  void acceptsSignedIdAndRejectsTamperedIdRequestAndSignature() throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-only-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature =
        "ts=1794225600,v1="
            + HexFormat.of()
                .formatHex(
                    mac.doFinal(
                        "id:42;request-id:request-id;ts:1794225600;"
                            .getBytes(StandardCharsets.UTF_8)));
    verifier(true).verify("42", "request-id", signature);
    assertThatThrownBy(() -> verifier(true).verify("43", "request-id", signature))
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(() -> verifier(true).verify("42", "other-request", signature))
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(() -> verifier(true).verify("42", "request-id", signature + ",v1=00"))
        .isInstanceOf(PaymentException.class);
    assertThatThrownBy(() -> verifier(true).verify("42", "request-id", "invalid"))
        .isInstanceOf(PaymentException.class);
  }

  @Test
  void refusesNotificationsWhenDisabled() {
    assertThatThrownBy(() -> verifier(false).verify("42", "request-id", "signature"))
        .isInstanceOf(PaymentException.class)
        .extracting(e -> ((PaymentException) e).status().value())
        .isEqualTo(503);
  }
}
