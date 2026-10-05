package com.vocenocoracao.supermercado_api.product.dto;

import java.util.UUID;

public record ProductCategoryDTO(
        UUID id,
        String name
) {
}
