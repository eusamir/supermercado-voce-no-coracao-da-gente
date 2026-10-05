package com.vocenocoracao.supermercado_api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record ProductFilterDTO(
        @Schema(description = "Partial, case-insensitive product name", example = "leite")
        String search,
        @Schema(description = "Filter by category id")
        UUID categoryId,
        @Schema(description = "Filter by active status (administrative listing only)")
        Boolean active
) {
}
