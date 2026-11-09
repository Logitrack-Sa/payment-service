package com.logitrack.payment.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
  java.util.List<PaymentOrder> findTop5ByNextReconcileAtLessThanEqualOrderByNextReconcileAtAsc(
      java.time.Instant now);

  Optional<PaymentOrder> findByShipmentId(String shipmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentOrder p where p.shipmentId=:shipmentId")
  Optional<PaymentOrder> lockByShipmentId(String shipmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentOrder p where p.id=:id")
  Optional<PaymentOrder> lockById(String id);
}
