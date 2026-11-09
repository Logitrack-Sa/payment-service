package com.logitrack.payment.domain;

import com.logitrack.payment.api.PaymentException;
import com.logitrack.payment.client.MercadoPagoClient;
import com.logitrack.payment.client.ProviderPaymentSnapshot;
import com.logitrack.payment.client.ShipmentQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStore {
  private final PaymentOrderRepository orders;
  private final ProviderPaymentRepository payments;
  private final WebhookInboxRepository inbox;

  public record Reservation(PaymentOrder order, boolean create) {}

  @Transactional(readOnly = true)
  public Optional<PaymentOrder> find(String shipmentId) {
    return orders.findByShipmentId(shipmentId);
  }

  @Transactional
  public Reservation reserve(ShipmentQuote quote, String collector, boolean sandbox) {
    var existing = orders.lockByShipmentId(quote.id());
    if (existing.isPresent()) {
      var order = existing.get();
      if (order.amount.compareTo(quote.fee()) != 0
          || !order.customerId.equals(quote.customerId())
          || !order.collectorId.equals(collector)
          || order.sandbox != sandbox)
        throw conflict(
            "PAYMENT_CHANGED", "La información del pago cambió. Contactá a administración.");
      if (!Set.of(PaymentStatus.PENDING, PaymentStatus.REJECTED, PaymentStatus.CANCELLED)
          .contains(order.status))
        throw conflict(
            "PAYMENT_NOT_PAYABLE", "Este envío no admite otro pago. Consultá su estado.");
      if ("READY".equals(order.checkoutState) && order.checkoutUrl != null)
        return new Reservation(order, false);
      throw conflict(
          "CHECKOUT_REVIEW_REQUIRED",
          "Ya se inició una solicitud. Estamos verificando su resultado antes de generar otro"
              + " enlace.");
    }
    var order =
        new PaymentOrder(
            quote.id(), quote.customerId(), quote.trackingCode(), quote.fee(), collector, sandbox);
    return new Reservation(orders.saveAndFlush(order), true);
  }

  @Transactional
  public PaymentOrder complete(String id, MercadoPagoClient.Checkout checkout) {
    var order = orders.lockById(id).orElseThrow();
    order.preferenceId = checkout.id();
    order.checkoutUrl = checkout.url();
    order.checkoutState = "READY";
    order.updatedAt = Instant.now();
    return order;
  }

  @Transactional
  public void uncertain(String id) {
    var order = orders.lockById(id).orElseThrow();
    order.checkoutState = "REVIEW_REQUIRED";
    order.updatedAt = Instant.now();
  }

  @Transactional(readOnly = true)
  public List<PaymentOrder> dueOrders() {
    return orders.findTop5ByNextReconcileAtLessThanEqualOrderByNextReconcileAtAsc(Instant.now());
  }

  @Transactional
  public void pollStarted(String id) {
    var order = orders.lockById(id).orElseThrow();
    order.nextReconcileAt =
        Instant.now().plusSeconds(order.status == PaymentStatus.APPROVED ? 300 : 60);
  }

  @Transactional
  public void enqueue(String id) {
    var item = inbox.findById(id).orElseGet(() -> new WebhookInbox(id));
    item.pending = true;
    item.generation++;
    item.attempts = 0;
    item.nextAttemptAt = Instant.now();
    item.result = null;
    inbox.saveAndFlush(item);
  }

  @Transactional(readOnly = true)
  public List<WebhookInbox> due() {
    return inbox.findTop5ByPendingTrueAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        Instant.now());
  }

  @Transactional
  public void reconcile(String id, long generation, ProviderPaymentSnapshot snapshot) {
    var item = inbox.findById(id).orElseThrow();
    var found =
        orders.lockById(snapshot.externalReference() == null ? "" : snapshot.externalReference());
    if (found.isEmpty()) {
      finish(item, generation, "UNRELATED");
      return;
    }
    var order = found.get();
    if (!valid(order, snapshot)) {
      order.needsReview = true;
      order.status = PaymentStatus.REVIEW_REQUIRED;
      order.updatedAt = Instant.now();
      finish(item, generation, "INVALID_PAYMENT");
      return;
    }
    var previous = payments.findById(id);
    if (previous.isPresent() && !previous.get().orderId.equals(order.id)) {
      finish(item, generation, "REFERENCE_CONFLICT");
      return;
    }
    if (previous.isPresent() && !snapshot.updatedAt().isAfter(previous.get().providerUpdatedAt)) {
      finish(item, generation, "ALREADY_CURRENT");
      return;
    }
    var payment = previous.orElseGet(() -> new ProviderPayment(id, order.id));
    payment.status = snapshot.status();
    payment.providerUpdatedAt = snapshot.updatedAt();
    payment.refundedAmount =
        snapshot.refundedAmount() == null ? BigDecimal.ZERO : snapshot.refundedAmount();
    payments.saveAndFlush(payment);
    var all = payments.findByOrderId(order.id);
    long approved = all.stream().filter(p -> "approved".equals(p.status)).count();
    boolean partial =
        all.stream().anyMatch(p -> "approved".equals(p.status) && p.refundedAmount.signum() > 0);
    if (approved > 1 || partial) order.needsReview = true;
    order.status = aggregate(all, order.needsReview);
    if (order.checkoutUrl != null)
      order.checkoutState =
          order.status == PaymentStatus.PENDING ? "AWAITING_CONFIRMATION" : "READY";
    order.updatedAt = Instant.now();
    finish(item, generation, "VERIFIED");
  }

  @Transactional
  public void retry(String id, long generation) {
    var item = inbox.findById(id).orElseThrow();
    if (item.generation != generation) return;
    item.attempts = Math.min(item.attempts + 1, 20);
    item.nextAttemptAt =
        Instant.now().plusSeconds(Math.min(3600, 10L << Math.min(item.attempts, 10)));
    item.result = "RETRY_PENDING";
  }

  private void finish(WebhookInbox item, long generation, String result) {
    item.result = result;
    if (item.generation == generation) item.pending = false;
  }

  private boolean valid(PaymentOrder o, ProviderPaymentSnapshot p) {
    return p.amount() != null
        && o.amount.compareTo(p.amount()) == 0
        && o.currency.equals(p.currency())
        && o.collectorId.equals(p.collectorId())
        && p.liveMode() != null
        && p.liveMode() != o.sandbox
        && p.updatedAt() != null
        && Set.of(
                "pending",
                "in_process",
                "authorized",
                "approved",
                "rejected",
                "cancelled",
                "refunded",
                "charged_back")
            .contains(p.status() == null ? "" : p.status())
        && (p.refundedAmount() == null
            || (p.refundedAmount().signum() >= 0 && p.refundedAmount().compareTo(o.amount) <= 0));
  }

  private PaymentStatus aggregate(List<ProviderPayment> all, boolean review) {
    if (review) return PaymentStatus.REVIEW_REQUIRED;
    if (all.stream().anyMatch(p -> "charged_back".equals(p.status)))
      return PaymentStatus.CHARGED_BACK;
    if (all.stream().anyMatch(p -> "approved".equals(p.status))) return PaymentStatus.APPROVED;
    if (all.stream().anyMatch(p -> "refunded".equals(p.status))) return PaymentStatus.REFUNDED;
    var last = all.stream().max(Comparator.comparing(p -> p.providerUpdatedAt)).orElseThrow();
    return switch (last.status) {
      case "rejected" -> PaymentStatus.REJECTED;
      case "cancelled" -> PaymentStatus.CANCELLED;
      default -> PaymentStatus.PENDING;
    };
  }

  private PaymentException conflict(String code, String message) {
    return new PaymentException(HttpStatus.CONFLICT, code, message);
  }
}
