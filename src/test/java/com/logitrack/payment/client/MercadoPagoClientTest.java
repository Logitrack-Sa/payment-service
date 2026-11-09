package com.logitrack.payment.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.config.PaymentProperties;
import com.logitrack.payment.domain.PaymentOrder;
import java.math.BigDecimal;
import java.net.URI;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoClientTest {
  MockRestServiceServer server;
  MercadoPagoClient client;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder().baseUrl("https://api.mercadopago.com");
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new MercadoPagoClient(
            builder.build(),
            new PaymentProperties(
                true,
                true,
                "TEST-private",
                "secret",
                URI.create("https://logitrack.example/logitrack"),
                URI.create("https://api.mercadopago.com"),
                URI.create("http://shipment-service")));
  }

  @Test
  void preferenceUsesServerAmountAndSafeReturnUrls() {
    var order = mock(PaymentOrder.class);
    when(order.getId()).thenReturn("order-uuid");
    when(order.getShipmentId()).thenReturn("shipment-uuid");
    when(order.getTrackingCode()).thenReturn("LT-A1B2C3D4E5");
    when(order.getAmount()).thenReturn(new BigDecimal("2300.00"));
    server
        .expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer TEST-private"))
        .andExpect(jsonPath("$.items[0].unit_price").value(2300.0))
        .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
        .andExpect(jsonPath("$.external_reference").value("order-uuid"))
        .andExpect(
            jsonPath("$.notification_url")
                .value("https://logitrack.example/logitrack/api/v1/payments/webhooks/mercadopago"))
        .andRespond(
            withSuccess(
                "{\"id\":\"pref1\",\"sandbox_init_point\":\"https://sandbox.mercadopago.com/checkout?pref_id=pref1\"}",
                MediaType.APPLICATION_JSON));
    assertThat(client.createCheckout(order).id()).isEqualTo("pref1");
    server.verify();
  }

  @Test
  void doesNotPropagateProviderErrorsOrSecrets() {
    server
        .expect(requestTo("https://api.mercadopago.com/users/me"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-provider-debug-token"));
    assertThatThrownBy(() -> client.collectorId())
        .isInstanceOf(PaymentException.class)
        .hasMessageNotContaining("private-provider-debug-token");
  }

  @Test
  void rejectsNonMercadoPagoAndUnsafeCheckoutUrls() {
    for (String url :
        new String[] {
          "https://mercadopago.com.evil.test/pay",
          "javascript:alert(1)",
          "http://mercadopago.com/pay",
          "https://user:pass@mercadopago.com/pay",
          "https://mercadopago.com:8443/pay"
        }) assertThat(MercadoPagoClient.validCheckoutUrl(url)).isFalse();
    assertThat(
            MercadoPagoClient.validCheckoutUrl(
                "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123"))
        .isTrue();
  }

  @Test
  void verifiesReturnedPaymentId() {
    server
        .expect(requestTo("https://api.mercadopago.com/v1/payments/42"))
        .andRespond(withSuccess("{\"id\":43}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.payment("42")).isInstanceOf(PaymentException.class);
  }
}
