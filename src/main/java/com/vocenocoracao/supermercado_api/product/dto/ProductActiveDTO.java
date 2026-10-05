package com.vocenocoracao.supermercado_api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ProductActiveDTO(
        @Schema(description = "Desired state: true activates, false deactivates", example = "false")
        @NotNull(message = "O campo active é obrigatório.")
        Boolean active
) {
}
