package com.vocenocoracao.supermercado_api.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record CategoryFilterDTO(
        @Schema(description = "Search term")
        String search,
        @Schema(description = "Filter by active status")
        Boolean active,
        @Schema(description = "Filter from created date")
        LocalDate createdAtFrom,
        @Schema(description = "Filter to created date")
        LocalDate createdAtTo
) {
}
