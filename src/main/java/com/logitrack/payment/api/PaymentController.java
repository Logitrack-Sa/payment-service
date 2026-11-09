package com.logitrack.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.logitrack.payment.domain.PaymentStore;
import com.logitrack.payment.service.PaymentService;
import com.logitrack.payment.service.WebhookVerifier;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService service;
  private final PaymentStore store;
  private final WebhookVerifier signatures;

  @GetMapping("/shipments/{shipmentId}")
  public ResponseEntity<PaymentResponse> find(
      @PathVariable UUID shipmentId, @AuthenticationPrincipal Jwt caller) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.find(shipmentId, caller));
  }

  @PostMapping("/shipments/{shipmentId}/checkout")
  public ResponseEntity<PaymentResponse> checkout(
      @PathVariable UUID shipmentId, @AuthenticationPrincipal Jwt caller) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.checkout(shipmentId, caller));
  }

  @PostMapping("/webhooks/mercadopago")
  public Map<String, Boolean> webhook(
      @RequestParam(name = "data.id", required = false) String id,
      @RequestHeader(name = "x-request-id", required = false) String requestId,
      @RequestHeader(name = "x-signature", required = false) String signature,
      @RequestBody JsonNode body) {
    signatures.verify(id, requestId, signature);
    if (!"payment".equals(body.path("type").asText())
        || !Objects.equals(id, body.path("data").path("id").asText()))
      throw new PaymentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_NOTIFICATION",
          "La notificación no coincide con el recurso firmado.");
    store.enqueue(id);
    return Map.of("received", true);
  }
}
