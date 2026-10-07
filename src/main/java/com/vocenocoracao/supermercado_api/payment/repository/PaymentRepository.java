package com.vocenocoracao.supermercado_api.payment.repository;

import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    @Query("""
            select payment from Payment payment
            join fetch payment.order
            where payment.status = com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus.PENDING
              and payment.createdAt < :threshold
            order by payment.createdAt
            """)
    List<Payment> findStalledPending(@Param("threshold") Instant threshold, Pageable pageable);

    @Query("""
            select payment from Payment payment
            join fetch payment.order order
            where payment.status = com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus.APPROVED
              and order.status = com.vocenocoracao.supermercado_api.order.entity.OrderStatus.PAYMENT_PENDING
              and payment.updatedAt < :threshold
            order by payment.updatedAt
            """)
    List<Payment> findStalledApproved(@Param("threshold") Instant threshold, Pageable pageable);
}
