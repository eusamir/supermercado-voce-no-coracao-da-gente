package com.vocenocoracao.supermercado_api.order.dto;

import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import java.math.BigDecimal;

public record OrderPaymentResponseDTO(
        PaymentStatus status,
        BigDecimal amount
) {
}
