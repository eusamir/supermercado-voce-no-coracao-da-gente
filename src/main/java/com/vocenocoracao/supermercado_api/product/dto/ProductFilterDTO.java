package com.vocenocoracao.supermercado_api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record ProductFilterDTO(
        @Schema(description = "Partial, case-insensitive product name", example = "leite")
        String search,
        @Schema(description = "Filter by category id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID categoryId,
        @Schema(description = "Filter by active status (administrative listing only)")
        Boolean active
) {
}
