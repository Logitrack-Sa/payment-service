package com.logitrack.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder {
  @Id
  @Column(length = 36)
  String id;

  @Column(nullable = false, unique = true, length = 36)
  String shipmentId;

  @Column(nullable = false, length = 36)
  String customerId;

  @Column(nullable = false, length = 64)
  String trackingCode;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal amount;

  @Column(nullable = false, length = 3)
  String currency;

  @Column(nullable = false, length = 40)
  String collectorId;

  @Column(nullable = false)
  boolean sandbox;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 24)
  PaymentStatus status;

  @Column(nullable = false)
  boolean needsReview;

  @Column(nullable = false, length = 24)
  String checkoutState;

  @Column(length = 100)
  String preferenceId;

  @Column(length = 1024)
  String checkoutUrl;

  @Column(nullable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  @Column(nullable = false)
  Instant nextReconcileAt;

  @Version Long version;

  PaymentOrder(
      String shipmentId,
      String customerId,
      String trackingCode,
      BigDecimal amount,
      String collectorId,
      boolean sandbox) {
    this.id = UUID.randomUUID().toString();
    this.shipmentId = shipmentId;
    this.customerId = customerId;
    this.trackingCode = trackingCode;
    this.amount = amount;
    this.currency = "ARS";
    this.collectorId = collectorId;
    this.sandbox = sandbox;
    this.status = PaymentStatus.PENDING;
    this.checkoutState = "CREATING";
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
    this.nextReconcileAt = createdAt.plusSeconds(60);
  }
}
