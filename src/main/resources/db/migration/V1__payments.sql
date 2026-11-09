CREATE TABLE payment_orders (
 id VARCHAR(36) PRIMARY KEY, shipment_id VARCHAR(36) NOT NULL UNIQUE,
 customer_id VARCHAR(36) NOT NULL, tracking_code VARCHAR(64) NOT NULL,
 amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 collector_id VARCHAR(40) NOT NULL, sandbox BOOLEAN NOT NULL,
 status VARCHAR(24) NOT NULL, needs_review BOOLEAN NOT NULL, checkout_state VARCHAR(24) NOT NULL,
 preference_id VARCHAR(100), checkout_url VARCHAR(1024),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, next_reconcile_at DATETIME(6) NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE provider_payments (
 id VARCHAR(40) PRIMARY KEY, order_id VARCHAR(36) NOT NULL,
 status VARCHAR(32) NOT NULL, refunded_amount DECIMAL(19,2) NOT NULL,
 provider_updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_provider_order FOREIGN KEY (order_id) REFERENCES payment_orders(id)
);
CREATE INDEX idx_provider_order ON provider_payments(order_id);
CREATE TABLE webhook_inbox (
 id VARCHAR(40) PRIMARY KEY, pending BOOLEAN NOT NULL, generation BIGINT NOT NULL,
 attempts INTEGER NOT NULL, next_attempt_at DATETIME(6) NOT NULL,
 result VARCHAR(40), version BIGINT NOT NULL
);
CREATE INDEX idx_inbox_pending ON webhook_inbox(pending, next_attempt_at);
