package com.vocenocoracao.supermercado_api.category.controller.converter;

import com.vocenocoracao.supermercado_api.category.dto.CategoryResponseDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class CategoryToCategoryResponseDTOConverter implements Converter<Category, CategoryResponseDTO> {

    @Override
    public CategoryResponseDTO convert(MappingContext<Category, CategoryResponseDTO> context) {
        Category category = context.getSource();

        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
