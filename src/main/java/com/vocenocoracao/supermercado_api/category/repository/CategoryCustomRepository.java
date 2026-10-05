package com.vocenocoracao.supermercado_api.category.repository;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface CategoryCustomRepository {
    List<Category> findAll(CategoryFilterDTO filter, Pageable pageable);
    long count(CategoryFilterDTO filter);
}
