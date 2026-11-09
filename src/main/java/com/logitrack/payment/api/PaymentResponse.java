package com.logitrack.payment.api;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    String id,
    String shipmentId,
    String status,
    BigDecimal amount,
    String currency,
    String checkoutState,
    String checkoutUrl,
    boolean enabled,
    boolean sandbox,
    Instant updatedAt) {}
