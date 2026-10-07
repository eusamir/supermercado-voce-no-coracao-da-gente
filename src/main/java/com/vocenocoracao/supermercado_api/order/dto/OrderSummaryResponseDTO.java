package com.vocenocoracao.supermercado_api.order.dto;

import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponseDTO(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        PaymentStatus paymentStatus
) {
}
