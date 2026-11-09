package com.logitrack.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookInbox {
  @Id
  @Column(length = 40)
  String id;

  @Column(nullable = false)
  boolean pending;

  @Column(nullable = false)
  long generation;

  @Column(nullable = false)
  int attempts;

  @Column(nullable = false)
  Instant nextAttemptAt;

  @Column(length = 40)
  String result;

  @Version Long version;

  WebhookInbox(String id) {
    this.id = id;
  }
}
