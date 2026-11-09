package com.logitrack.payment.service;

import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.api.PaymentResponse;
import com.logitrack.payment.client.MercadoPagoClient;
import com.logitrack.payment.client.ShipmentClient;
import com.logitrack.payment.client.ShipmentQuote;
import com.logitrack.payment.config.PaymentProperties;
import com.logitrack.payment.domain.PaymentOrder;
import com.logitrack.payment.domain.PaymentStatus;
import com.logitrack.payment.domain.PaymentStore;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final ShipmentClient shipments;
  private final MercadoPagoClient provider;
  private final PaymentStore store;
  private final PaymentProperties properties;

  public PaymentResponse find(UUID shipmentId, Jwt caller) {
    var quote = quote(shipmentId, caller);
    return response(quote, store.find(quote.id()).orElse(null));
  }

  public PaymentResponse checkout(UUID shipmentId, Jwt caller) {
    if (!properties.available())
      throw new PaymentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENTS_DISABLED",
          "El pago online todavía no está disponible.");
    if (!Boolean.TRUE.equals(caller.getClaim("email_verified")))
      throw new PaymentException(
          HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verificá tu correo antes de pagar.");
    var quote = quote(shipmentId, caller);
    if (!"PENDIENTE".equals(quote.status()) || quote.fee().signum() <= 0)
      throw new PaymentException(
          HttpStatus.CONFLICT,
          "SHIPMENT_NOT_PAYABLE",
          "Solo se puede pagar un envío pendiente con tarifa válida.");
    quote.fee().setScale(2, RoundingMode.UNNECESSARY);
    var reservation = store.reserve(quote, provider.collectorId(), properties.sandbox());
    if (!reservation.create()) return response(quote, reservation.order());
    MercadoPagoClient.Checkout checkout;
    try {
      checkout = provider.createCheckout(reservation.order());
    } catch (RuntimeException failure) {
      store.uncertain(reservation.order().getId());
      throw failure;
    }
    return response(quote, store.complete(reservation.order().getId(), checkout));
  }

  private ShipmentQuote quote(UUID id, Jwt caller) {
    var q = shipments.find(id, caller.getTokenValue());
    List<String> roles = caller.getClaimAsStringList("roles");
    if (!roles.contains("ADMIN") && !q.customerId().equals(caller.getClaimAsString("customerId")))
      throw new PaymentException(
          HttpStatus.FORBIDDEN, "SHIPMENT_ACCESS", "No tenés acceso al pago de ese envío.");
    return q;
  }

  private PaymentResponse response(ShipmentQuote q, PaymentOrder o) {
    String url =
        o != null
                && Set.of(PaymentStatus.PENDING, PaymentStatus.REJECTED, PaymentStatus.CANCELLED)
                    .contains(o.getStatus())
                && "READY".equals(o.getCheckoutState())
            ? o.getCheckoutUrl()
            : null;
    return new PaymentResponse(
        o == null ? null : o.getId(),
        q.id(),
        o == null ? "NOT_STARTED" : o.getStatus().name(),
        o == null ? q.fee() : o.getAmount(),
        "ARS",
        o == null ? null : o.getCheckoutState(),
        url,
        properties.available(),
        properties.sandbox(),
        o == null ? null : o.getUpdatedAt());
  }
}
