package com.vocenocoracao.supermercado_api.product.controller.converter;

import com.vocenocoracao.supermercado_api.product.dto.ProductRequestDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class ProductRequestDTOToProductConverter implements Converter<ProductRequestDTO, Product> {

    @Override
    public Product convert(MappingContext<ProductRequestDTO, Product> context) {
        ProductRequestDTO productRequestDTO = context.getSource();
        Product product = context.getDestination();

        if (product == null) {
            product = new Product();
            product.setActive(true);
        }

        product.setName(productRequestDTO.name().trim());
        product.setDescription(productRequestDTO.description());
        product.setPrice(productRequestDTO.price());
        product.setStock(productRequestDTO.stock());
        return product;
    }
}
