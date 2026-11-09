package com.logitrack.payment.service;

import com.logitrack.payment.client.MercadoPagoClient;
import com.logitrack.payment.config.PaymentProperties;
import com.logitrack.payment.domain.PaymentStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentReconciler {
  private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);
  private final PaymentStore store;
  private final MercadoPagoClient provider;
  private final PaymentProperties properties;

  @Scheduled(
      initialDelayString = "${logitrack.payments.reconciliation-initial-delay-ms:10000}",
      fixedDelayString = "${logitrack.payments.reconciliation-delay-ms:10000}")
  public void reconcile() {
    if (!properties.available()) return;
    for (var order : store.dueOrders()) {
      store.pollStarted(order.getId());
      try {
        for (String id : provider.paymentIds(order.getId())) store.enqueue(id);
      } catch (RuntimeException unavailable) {
        log.warn(
            "Consulta de pagos pendientes no disponible; se reintentará sin crear otro cobro.");
      }
    }
    for (var item : store.due()) {
      try {
        store.reconcile(item.getId(), item.getGeneration(), provider.payment(item.getId()));
      } catch (RuntimeException unavailable) {
        store.retry(item.getId(), item.getGeneration());
        log.warn(
            "Conciliación de pago pendiente; se reintentará sin modificar el estado confirmado.");
      }
    }
  }
}
