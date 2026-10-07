package com.vocenocoracao.supermercado_api.category.controller.converter;

import com.vocenocoracao.supermercado_api.category.dto.CategoryRequestDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class CategoryRequestDTOToCategoryConverter implements Converter<CategoryRequestDTO, Category> {

    @Override
    public Category convert(MappingContext<CategoryRequestDTO, Category> context) {
        CategoryRequestDTO categoryRequestDTO = context.getSource();
        Category category = context.getDestination();

        if (category == null) {
            category = new Category();
            category.setActive(true);
        }

        category.setName(categoryRequestDTO.name().trim());
        return category;
    }
}
