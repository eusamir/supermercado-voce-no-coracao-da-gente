package com.vocenocoracao.supermercado_api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequestDTO(
        @Schema(description = "Product name", example = "Leite integral 1L")
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.")
        String name,

        @Schema(description = "Product description", example = "Leite UHT integral")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.")
        String description,

        @Schema(description = "Unit price", example = "5.29")
        @NotNull(message = "O preço é obrigatório.")
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
        @Digits(integer = 17, fraction = 2, message = "O preço deve ter no máximo 2 casas decimais.")
        BigDecimal price,

        @Schema(description = "Available stock", example = "200")
        @NotNull(message = "O estoque é obrigatório.")
        @Min(value = 0, message = "O estoque não pode ser negativo.")
        Integer stock,

        @Schema(description = "Category id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "A categoria é obrigatória.")
        UUID categoryId
) {
}
