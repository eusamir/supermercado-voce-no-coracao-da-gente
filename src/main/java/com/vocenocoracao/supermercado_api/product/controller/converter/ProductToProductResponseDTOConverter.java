package com.vocenocoracao.supermercado_api.product.controller.converter;

import com.vocenocoracao.supermercado_api.product.dto.ProductCategoryDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class ProductToProductResponseDTOConverter implements Converter<Product, ProductResponseDTO> {

    @Override
    public ProductResponseDTO convert(MappingContext<Product, ProductResponseDTO> context) {
        Product product = context.getSource();

        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                new ProductCategoryDTO(product.getCategory().getId(), product.getCategory().getName())
        );
    }
}
