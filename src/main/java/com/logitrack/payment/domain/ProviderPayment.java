package com.logitrack.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "provider_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderPayment {
  @Id
  @Column(length = 40)
  String id;

  @Column(nullable = false, length = 36)
  String orderId;

  @Column(nullable = false, length = 32)
  String status;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal refundedAmount;

  @Column(nullable = false)
  Instant providerUpdatedAt;

  ProviderPayment(String id, String orderId) {
    this.id = id;
    this.orderId = orderId;
  }
}
