package com.logitrack.payment.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderPaymentRepository extends JpaRepository<ProviderPayment, String> {
  List<ProviderPayment> findByOrderId(String orderId);
}
