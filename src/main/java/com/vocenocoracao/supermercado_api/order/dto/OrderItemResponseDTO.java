package com.vocenocoracao.supermercado_api.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponseDTO(
        UUID productId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
) {
}
