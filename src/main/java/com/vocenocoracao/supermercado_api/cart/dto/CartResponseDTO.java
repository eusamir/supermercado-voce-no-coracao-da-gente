package com.vocenocoracao.supermercado_api.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponseDTO(
        UUID id,
        List<CartItemResponseDTO> items,
        int itemCount,
        BigDecimal total
) {
}
