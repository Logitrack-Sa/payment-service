package com.logitrack.payment.service;

import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.config.PaymentProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookVerifier {
  private final PaymentProperties properties;

  public void verify(String id, String requestId, String signature) {
    if (!properties.available())
      throw new PaymentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENTS_DISABLED",
          "La recepción de pagos no está habilitada.");
    try {
      if (id == null
          || !id.matches("[0-9]{1,40}")
          || requestId == null
          || !requestId.matches("[A-Za-z0-9-]{1,128}")
          || signature == null
          || signature.length() > 512) throw new IllegalArgumentException();
      var values = new HashMap<String, String>();
      for (String part : signature.split(",")) {
        String[] pair = part.trim().split("=", 2);
        if (pair.length != 2 || values.put(pair[0], pair[1]) != null)
          throw new IllegalArgumentException();
      }
      String ts = values.get("ts"), v1 = values.get("v1");
      if (ts == null || !ts.matches("[0-9]{10,16}") || v1 == null || !v1.matches("[0-9a-fA-F]{64}"))
        throw new IllegalArgumentException();
      String manifest = "id:" + id + ";request-id:" + requestId + ";ts:" + ts + ";";
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      if (!MessageDigest.isEqual(
          mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(v1)))
        throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw new PaymentException(
          HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Firma de notificación inválida.");
    }
  }
}
