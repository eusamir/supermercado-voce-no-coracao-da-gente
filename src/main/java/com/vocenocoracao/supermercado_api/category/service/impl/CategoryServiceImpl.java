package com.vocenocoracao.supermercado_api.category.service.impl;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.repository.CategoryRepository;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.config.CacheConfig;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static final String ALREADY_EXISTS = "Categoria já cadastrada.";
    private static final String NOT_FOUND = "Categoria não encontrada.";

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public Category create(Category category) {
        validateUniqueName(category.getName(), null);
        return saveAndFlush(category);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public Category update(Category category) {
        validateUniqueName(category.getName(), category.getId());
        return saveAndFlush(category);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public Category changeActive(UUID id, boolean active) {
        Category category = getOrThrow(id);

        if (category.isActive() != active) {
            category.setActive(active);
            category = categoryRepository.save(category);
        }

        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public Category findById(UUID id) {
        return getOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Category findActiveById(UUID id) {
        Category category = getOrThrow(id);

        if (!category.isActive()) {
            throw new NotFoundException(NOT_FOUND);
        }

        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> findAll(CategoryFilterDTO filter, Pageable pageable) {
        List<Category> content = categoryRepository.findAll(filter, pageable);

        return new PageImpl<>(content, pageable, categoryRepository.count(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> findAllActive(CategoryFilterDTO filter, Pageable pageable) {
        CategoryFilterDTO activeOnly = filter == null
                ? new CategoryFilterDTO(null, true, null, null)
                : new CategoryFilterDTO(filter.search(), true, filter.createdAtFrom(), filter.createdAtTo());

        return findAll(activeOnly, pageable);
    }

    private Category getOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private void validateUniqueName(String name, UUID currentId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new AlreadyExistsException(ALREADY_EXISTS);
                });
    }

    private Category saveAndFlush(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw new AlreadyExistsException(ALREADY_EXISTS);
        }
    }
}
