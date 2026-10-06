package com.vocenocoracao.supermercado_api.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponseDTO(
        UUID productId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        int stock,
        boolean available
) {
}
