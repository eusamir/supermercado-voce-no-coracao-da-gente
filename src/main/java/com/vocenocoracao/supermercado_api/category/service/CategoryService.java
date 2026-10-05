package com.vocenocoracao.supermercado_api.category.service;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    Category create(Category category);

    Category update(Category category);

    Category changeActive(UUID id, boolean active);

    Category findById(UUID id);

    Category findActiveById(UUID id);

    Page<Category> findAll(CategoryFilterDTO filter, Pageable pageable);

    Page<Category> findAllActive(CategoryFilterDTO filter, Pageable pageable);
}
