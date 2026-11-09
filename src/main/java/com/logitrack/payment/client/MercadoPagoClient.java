package com.logitrack.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.config.PaymentProperties;
import com.logitrack.payment.domain.PaymentOrder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MercadoPagoClient {
  public record Checkout(String id, String url) {}

  private static final Set<String> HOSTS =
      Set.of(
          "www.mercadopago.com.ar",
          "mercadopago.com.ar",
          "sandbox.mercadopago.com",
          "www.mercadopago.com",
          "mercadopago.com");
  private final RestClient client;
  private final PaymentProperties properties;
  private volatile String collectorId;

  public MercadoPagoClient(
      @Qualifier("mercadoPagoHttp") RestClient client, PaymentProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  public String collectorId() {
    if (collectorId != null) return collectorId;
    synchronized (this) {
      if (collectorId == null) {
        JsonNode account = get("/users/me", JsonNode.class);
        String id = account == null ? "" : account.path("id").asText();
        if (!id.matches("[0-9]{1,40}")) throw invalid();
        if (properties.sandbox()
            && !properties.accessToken().startsWith("TEST-")
            && !account.path("tags").toString().contains("test_user"))
          throw new PaymentException(
              HttpStatus.SERVICE_UNAVAILABLE,
              "TEST_CREDENTIALS_REQUIRED",
              "La integración necesita credenciales de prueba.");
        collectorId = id;
      }
    }
    return collectorId;
  }

  public Checkout createCheckout(PaymentOrder order) {
    String base = properties.publicUrl().toString().replaceAll("/+$", "");
    if (!"https".equals(URI.create(base).getScheme()))
      throw new PaymentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "HTTPS_REQUIRED",
          "El pago online requiere una dirección HTTPS configurada.");
    String back = base + "/shipments/" + order.getShipmentId() + "?payment=return";
    Map<String, Object> body =
        Map.of(
            "items",
            List.of(
                Map.of(
                    "id",
                    order.getShipmentId(),
                    "title",
                    "Envío " + order.getTrackingCode(),
                    "quantity",
                    1,
                    "currency_id",
                    "ARS",
                    "unit_price",
                    order.getAmount())),
            "external_reference",
            order.getId(),
            "back_urls",
            Map.of("success", back, "pending", back, "failure", back),
            "notification_url",
            base + "/api/v1/payments/webhooks/mercadopago",
            "auto_return",
            "approved");
    try {
      JsonNode result =
          client
              .post()
              .uri("/checkout/preferences")
              .headers(h -> h.setBearerAuth(properties.accessToken()))
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      String id = result == null ? "" : result.path("id").asText();
      String url =
          result == null
              ? ""
              : result.path(properties.sandbox() ? "sandbox_init_point" : "init_point").asText();
      if (id.isBlank() || id.length() > 100 || !validCheckoutUrl(url)) throw invalid();
      return new Checkout(id, url);
    } catch (RestClientException e) {
      throw unavailable();
    }
  }

  public List<String> paymentIds(String reference) {
    JsonNode result =
        get(
            "/v1/payments/search?external_reference="
                + UUID.fromString(reference)
                + "&sort=date_created&criteria=desc&limit=20",
            JsonNode.class);
    if (result == null || !result.path("results").isArray()) throw invalid();
    var ids = new ArrayList<String>();
    for (JsonNode payment : result.path("results")) {
      String id = payment.path("id").asText();
      if (!id.matches("[0-9]{1,40}")) throw invalid();
      ids.add(id);
    }
    return ids;
  }

  public ProviderPaymentSnapshot payment(String id) {
    if (id == null || !id.matches("[0-9]{1,40}")) throw invalid();
    ProviderPaymentSnapshot payment = get("/v1/payments/" + id, ProviderPaymentSnapshot.class);
    if (payment == null || !id.equals(payment.id())) throw invalid();
    return payment;
  }

  private <T> T get(String path, Class<T> type) {
    try {
      return client
          .get()
          .uri(path)
          .headers(h -> h.setBearerAuth(properties.accessToken()))
          .retrieve()
          .body(type);
    } catch (RestClientException e) {
      throw unavailable();
    }
  }

  public static boolean validCheckoutUrl(String value) {
    try {
      var u = URI.create(value);
      return "https".equals(u.getScheme())
          && HOSTS.contains(u.getHost())
          && u.getUserInfo() == null
          && (u.getPort() == -1 || u.getPort() == 443);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private PaymentException invalid() {
    return new PaymentException(
        HttpStatus.BAD_GATEWAY,
        "INVALID_PROVIDER_RESPONSE",
        "Mercado Pago devolvió una respuesta que no pudimos validar.");
  }

  private PaymentException unavailable() {
    return new PaymentException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PROVIDER_UNAVAILABLE",
        "Mercado Pago no está disponible. El estado del pago se confirmará antes de volver a"
            + " intentarlo.");
  }
}
