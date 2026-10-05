package com.vocenocoracao.supermercado_api.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        boolean active,
        ProductCategoryDTO category
) {
}
