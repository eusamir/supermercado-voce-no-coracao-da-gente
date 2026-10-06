package com.vocenocoracao.supermercado_api.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemUpdateDTO(
        @Schema(description = "New quantity; zero removes the item", example = "3")
        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 0, message = "A quantidade não pode ser negativa.")
        @Max(value = 999, message = "A quantidade deve ser de no máximo 999.")
        Integer quantity
) {
}
