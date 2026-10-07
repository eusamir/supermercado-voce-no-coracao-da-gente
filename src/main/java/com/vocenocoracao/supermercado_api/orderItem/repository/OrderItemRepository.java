package com.vocenocoracao.supermercado_api.orderItem.repository;

import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findAllByOrderId(UUID orderId);

    @Query("""
            select orderItem from OrderItem orderItem
            join fetch orderItem.product
            where orderItem.order.id = :orderId
            order by orderItem.name, orderItem.id
            """)
    List<OrderItem> findAllWithProductByOrderId(@Param("orderId") UUID orderId);
}
