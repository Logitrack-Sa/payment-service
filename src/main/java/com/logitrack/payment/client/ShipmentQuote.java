package com.logitrack.payment.client;

import java.math.BigDecimal;

public record ShipmentQuote(
    String id, String customerId, String trackingCode, BigDecimal fee, String status) {}
