package com.logitrack.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.logitrack.payment.client.*;
import com.logitrack.payment.domain.*;
import com.logitrack.payment.service.PaymentReconciler;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired PaymentOrderRepository orders;
  @Autowired ProviderPaymentRepository payments;
  @Autowired WebhookInboxRepository inbox;
  @Autowired PaymentReconciler reconciler;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean ShipmentClient shipments;
  @MockitoBean MercadoPagoClient provider;
  @MockitoBean JwtDecoder decoder;
  final String shipment = UUID.randomUUID().toString(), customer = UUID.randomUUID().toString();
  final Instant updated = Instant.parse("2026-11-09T12:00:00Z");

  String path() {
    return "/api/v1/payments/shipments/" + shipment;
  }

  JwtRequestPostProcessor client() {
    return identity("CLIENTE", customer, true);
  }

  JwtRequestPostProcessor identity(String role, String customerId, boolean verified) {
    return jwt()
        .jwt(j ->
            j.subject(UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .claim("customerId", customerId)
                .claim("email_verified", verified))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @BeforeEach
  void setup() {
    payments.deleteAll();
    inbox.deleteAll();
    orders.deleteAll();
    when(shipments.find(eq(UUID.fromString(shipment)), anyString()))
        .thenReturn(
            new ShipmentQuote(
                shipment, customer, "LT-A1B2C3D4E5", new BigDecimal("2300.00"), "PENDIENTE"));
    when(provider.collectorId()).thenReturn("1234");
    when(provider.createCheckout(any()))
        .thenReturn(
            new MercadoPagoClient.Checkout(
                "pref-123", "https://sandbox.mercadopago.com/checkout?pref_id=123"));
  }

  PaymentOrder start() throws Exception {
    mvc.perform(post(path() + "/checkout").with(client()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(2300))
        .andExpect(jsonPath("$.status").value("PENDING"));
    return orders.findByShipmentId(shipment).orElseThrow();
  }

  ProviderPaymentSnapshot snapshot(String id, String order, String state, Instant date) {
    return new ProviderPaymentSnapshot(
        id, state, order, new BigDecimal("2300.00"), "ARS", "1234", false, date, BigDecimal.ZERO);
  }

  String signature(String id) throws Exception {
    String ts = "1794225600";
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(
            "test-only-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "ts="
        + ts
        + ",v1="
        + HexFormat.of()
            .formatHex(
                mac.doFinal(
                    ("id:" + id + ";request-id:test-request;ts:" + ts + ";")
                        .getBytes(StandardCharsets.UTF_8)));
  }

  void notify(String id) throws Exception {
    mvc.perform(
            post("/api/v1/payments/webhooks/mercadopago")
                .param("data.id", id)
                .header("x-request-id", "test-request")
                .header("x-signature", signature(id))
                .contentType("application/json")
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + id + "\"}}"))
        .andExpect(status().isOk());
  }

  @Test
  void requiresAuthenticationAndBlocksCourierAndOtherCustomer() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(post(path() + "/checkout")).andExpect(status().isUnauthorized());
    mvc.perform(get(path()).with(identity("REPARTIDOR", customer, true)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path() + "/checkout")
                .with(identity("CLIENTE", UUID.randomUUID().toString(), true)))
        .andExpect(status().isForbidden());
    mvc.perform(post(path() + "/checkout").with(identity("CLIENTE", customer, false)))
        .andExpect(status().isForbidden());
    verify(provider, never()).createCheckout(any());
  }

  @Test
  void reusesCheckoutAndIgnoresBrowserAmountAndReturnStatus() throws Exception {
    var order = start();
    mvc.perform(
            post(path() + "/checkout")
                .with(client())
                .contentType("application/json")
                .content("{\"amount\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(order.getId()))
        .andExpect(jsonPath("$.amount").value(2300));
    verify(provider, times(1)).createCheckout(any());
    mvc.perform(get(path() + "?status=approved&payment_id=999").with(client()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void rejectsPaymentForShipmentsAlreadyDispatched() throws Exception {
    when(shipments.find(any(), anyString()))
        .thenReturn(
            new ShipmentQuote(
                shipment, customer, "LT-A1B2C3D4E5", new BigDecimal("2300.00"), "EN_RUTA"));
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isConflict());
    assertThat(orders.count()).isZero();
  }

  @Test
  void uncertainCreationDoesNotCreateAnotherCheckout() throws Exception {
    when(provider.createCheckout(any()))
        .thenThrow(
            new com.logitrack.payment.api.PaymentException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_UNAVAILABLE",
                "No disponible"));
    mvc.perform(post(path() + "/checkout").with(client()))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isConflict());
    verify(provider, times(1)).createCheckout(any());
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getCheckoutState())
        .isEqualTo("REVIEW_REQUIRED");
  }

  @Test
  void rejectsForgedNotificationsWithoutChangingPayments() throws Exception {
    start();
    mvc.perform(
            post("/api/v1/payments/webhooks/mercadopago")
                .param("data.id", "42")
                .contentType("application/json")
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"42\"}}"))
        .andExpect(status().isUnauthorized());
    assertThat(inbox.count()).isZero();
  }

  @Test
  void signedNotificationsAreDurableAndOnlyApproveAfterProviderVerification() throws Exception {
    var order = start();
    notify("42");
    notify("42");
    assertThat(inbox.count()).isEqualTo(1);
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PENDING);
    when(provider.payment("42")).thenReturn(snapshot("42", order.getId(), "approved", updated));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.APPROVED);
    assertThat(inbox.findById("42").orElseThrow().isPending()).isFalse();
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isConflict());
    mvc.perform(get(path()).with(client()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkoutUrl").isEmpty());
  }

  @Test
  void delayedRejectedAttemptDoesNotUndoAnApprovedPayment() throws Exception {
    var order = start();
    notify("42");
    when(provider.payment("42")).thenReturn(snapshot("42", order.getId(), "approved", updated));
    reconciler.reconcile();
    notify("43");
    when(provider.payment("43"))
        .thenReturn(snapshot("43", order.getId(), "rejected", updated.minusSeconds(30)));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.APPROVED);
    notify("42");
    when(provider.payment("42"))
        .thenReturn(snapshot("42", order.getId(), "pending", updated.minusSeconds(40)));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.APPROVED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"amount", "currency", "collector", "mode"})
  void invalidProviderDataNeverApproves(String mismatch) throws Exception {
    var order = start();
    notify("42");
    when(provider.payment("42"))
        .thenReturn(
            new ProviderPaymentSnapshot(
                "42",
                "approved",
                order.getId(),
                mismatch.equals("amount") ? BigDecimal.ONE : new BigDecimal("2300.00"),
                mismatch.equals("currency") ? "USD" : "ARS",
                mismatch.equals("collector") ? "999" : "1234",
                mismatch.equals("mode"),
                updated,
                BigDecimal.ZERO));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REVIEW_REQUIRED);
  }

  @Test
  void pollingRecoversPaymentWhenTheWebhookWasLost() throws Exception {
    var order = start();
    jdbc.update(
        "update payment_orders set next_reconcile_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        order.getId());
    when(provider.paymentIds(order.getId())).thenReturn(List.of("42"));
    when(provider.payment("42")).thenReturn(snapshot("42", order.getId(), "approved", updated));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.APPROVED);
  }

  @Test
  void providerOutageKeepsNotificationForRetry() throws Exception {
    start();
    notify("42");
    when(provider.payment("42")).thenThrow(new IllegalStateException("network"));
    reconciler.reconcile();
    var pending = inbox.findById("42").orElseThrow();
    assertThat(pending.isPending()).isTrue();
    assertThat(pending.getNextAttemptAt()).isAfter(Instant.now());
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void duplicateApprovedPaymentsRequireReview() throws Exception {
    var order = start();
    for (String id : List.of("42", "43")) {
      notify(id);
      when(provider.payment(id)).thenReturn(snapshot(id, order.getId(), "approved", updated));
      reconciler.reconcile();
    }
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REVIEW_REQUIRED);
  }

  @Test
  void pendingProviderPaymentBlocksAnotherCheckoutUntilRejection() throws Exception {
    var order = start();
    notify("42");
    when(provider.payment("42")).thenReturn(snapshot("42", order.getId(), "in_process", updated));
    reconciler.reconcile();
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isConflict());
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getCheckoutState())
        .isEqualTo("AWAITING_CONFIRMATION");
    notify("42");
    when(provider.payment("42"))
        .thenReturn(snapshot("42", order.getId(), "rejected", updated.plusSeconds(20)));
    reconciler.reconcile();
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isOk());
    verify(provider, times(1)).createCheckout(any());
  }

  @Test
  void refundUpdatesStateAndCannotBePaidAgain() throws Exception {
    var order = start();
    notify("42");
    when(provider.payment("42")).thenReturn(snapshot("42", order.getId(), "approved", updated));
    reconciler.reconcile();
    notify("42");
    when(provider.payment("42"))
        .thenReturn(snapshot("42", order.getId(), "refunded", updated.plusSeconds(60)));
    reconciler.reconcile();
    assertThat(orders.findByShipmentId(shipment).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REFUNDED);
    mvc.perform(post(path() + "/checkout").with(client())).andExpect(status().isConflict());
  }
}
