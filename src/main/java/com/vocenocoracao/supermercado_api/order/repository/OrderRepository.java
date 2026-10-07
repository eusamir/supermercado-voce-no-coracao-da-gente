package com.vocenocoracao.supermercado_api.order.repository;

import com.vocenocoracao.supermercado_api.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select order from Order order where order.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            select new com.vocenocoracao.supermercado_api.order.repository.OrderSummary(
                order.id, order.status, order.total, order.createdAt, payment.status)
            from Payment payment
            join payment.order order
            where order.user.id = :userId
            order by order.createdAt desc, order.id desc
            """,
            countQuery = "select count(order) from Order order where order.user.id = :userId")
    Page<OrderSummary> findSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);
}
