package com.vocenocoracao.supermercado_api.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CartItemAddDTO(
        @Schema(description = "Product id")
        @NotNull(message = "O produto é obrigatório.")
        UUID productId,

        @Schema(description = "Quantity to add to the cart", example = "2")
        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1, message = "A quantidade deve ser de pelo menos 1.")
        @Max(value = 999, message = "A quantidade deve ser de no máximo 999.")
        Integer quantity
) {
}
