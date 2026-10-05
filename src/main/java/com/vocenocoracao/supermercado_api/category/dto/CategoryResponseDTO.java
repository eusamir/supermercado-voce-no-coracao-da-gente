package com.vocenocoracao.supermercado_api.category.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponseDTO(
        UUID id,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

}
