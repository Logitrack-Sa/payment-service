package com.logitrack.payment.domain;

public enum PaymentStatus {
  PENDING,
  APPROVED,
  REJECTED,
  REFUNDED,
  CHARGED_BACK,
  CANCELLED,
  REVIEW_REQUIRED
}
