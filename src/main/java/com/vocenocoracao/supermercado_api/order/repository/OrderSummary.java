package com.vocenocoracao.supermercado_api.order.repository;

import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        PaymentStatus paymentStatus
) {
}
