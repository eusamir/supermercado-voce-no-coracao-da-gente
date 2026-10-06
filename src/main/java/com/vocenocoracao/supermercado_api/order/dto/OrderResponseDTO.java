package com.vocenocoracao.supermercado_api.order.dto;

import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        List<OrderItemResponseDTO> items,
        OrderPaymentResponseDTO payment
) {
}
