package com.logitrack.payment.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, String> {
  List<WebhookInbox> findTop5ByPendingTrueAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
      Instant now);
}
