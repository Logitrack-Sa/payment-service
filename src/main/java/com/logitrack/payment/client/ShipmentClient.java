package com.logitrack.payment.client;

import com.logitrack.payment.api.PaymentException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ShipmentClient {
  private final RestClient client;

  public ShipmentClient(@Qualifier("shipmentHttp") RestClient client) {
    this.client = client;
  }

  public ShipmentQuote find(UUID id, String token) {
    try {
      var quote =
          client
              .get()
              .uri("/api/v1/shipments/{id}", id)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .retrieve()
              .body(ShipmentQuote.class);
      if (quote == null
          || !id.toString().equals(quote.id())
          || quote.customerId() == null
          || quote.fee() == null)
        throw new PaymentException(
            HttpStatus.BAD_GATEWAY, "INVALID_SHIPMENT", "No se pudo validar la tarifa del envío.");
      return quote;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 401
          || e.getStatusCode().value() == 403
          || e.getStatusCode().value() == 404)
        throw new PaymentException(
            HttpStatus.valueOf(e.getStatusCode().value()),
            "SHIPMENT_ACCESS",
            "No tenés acceso a ese envío o ya no está disponible.");
      throw unavailable();
    } catch (RestClientException e) {
      throw unavailable();
    }
  }

  private PaymentException unavailable() {
    return new PaymentException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SHIPMENT_UNAVAILABLE",
        "No se pudo consultar el envío. Intentá nuevamente más tarde.");
  }
}
