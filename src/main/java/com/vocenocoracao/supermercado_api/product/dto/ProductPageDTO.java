package com.vocenocoracao.supermercado_api.product.dto;

import java.util.List;

public record ProductPageDTO(List<ProductResponseDTO> content, long totalElements) {
}
