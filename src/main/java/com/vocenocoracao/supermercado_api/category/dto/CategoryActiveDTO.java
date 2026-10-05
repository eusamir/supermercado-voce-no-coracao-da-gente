package com.vocenocoracao.supermercado_api.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CategoryActiveDTO(
        @Schema(description = "Desired state: true activates, false deactivates", example = "false")
        @NotNull(message = "O campo active é obrigatório.")
        Boolean active
) {
}
