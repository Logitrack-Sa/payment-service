package com.logitrack.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record ProviderPaymentSnapshot(
    String id,
    String status,
    @JsonProperty("external_reference") String externalReference,
    @JsonProperty("transaction_amount") BigDecimal amount,
    @JsonProperty("currency_id") String currency,
    @JsonProperty("collector_id") String collectorId,
    @JsonProperty("live_mode") Boolean liveMode,
    @JsonProperty("date_last_updated") Instant updatedAt,
    @JsonProperty("transaction_amount_refunded") BigDecimal refundedAmount) {}
