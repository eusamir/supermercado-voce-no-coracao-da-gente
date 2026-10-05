package com.vocenocoracao.supermercado_api.category.controller;

import com.vocenocoracao.supermercado_api.category.dto.CategoryActiveDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryRequestDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryResponseDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {
    private final CategoryService categoryService;
    private final ModelMapper modelMapper;

    public CategoryController(CategoryService categoryService, ModelMapper modelMapper) {
        this.categoryService = categoryService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/api/categories")
    public Page<CategoryResponseDTO> findAllActive(
            @ParameterObject CategoryFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return categoryService.findAllActive(filter, pageable).map(this::toResponse);
    }

    @GetMapping("/api/categories/{id}")
    public CategoryResponseDTO findActiveById(@PathVariable UUID id) {
        return toResponse(categoryService.findActiveById(id));
    }

    @GetMapping("/api/admin/categories")
    public Page<CategoryResponseDTO> findAll(
            @ParameterObject CategoryFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return categoryService.findAll(filter, pageable).map(this::toResponse);
    }

    @GetMapping("/api/admin/categories/{id}")
    public CategoryResponseDTO findById(@PathVariable UUID id) {
        return toResponse(categoryService.findById(id));
    }

    @PostMapping("/api/categories")
    public ResponseEntity<CategoryResponseDTO> create(@Validated @RequestBody CategoryRequestDTO request) {
        Category category = categoryService.create(modelMapper.map(request, Category.class));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @PutMapping("/api/categories/{id}")
    public CategoryResponseDTO update(
            @PathVariable UUID id,
            @Validated @RequestBody CategoryRequestDTO request
    ) {
        Category category = categoryService.findById(id);
        modelMapper.map(request, category);
        return toResponse(categoryService.update(category));
    }

    @PatchMapping("/api/categories/{id}/active")
    public CategoryResponseDTO changeActive(
            @PathVariable UUID id,
            @Validated @RequestBody CategoryActiveDTO request
    ) {
        return toResponse(categoryService.changeActive(id, request.active()));
    }

    private CategoryResponseDTO toResponse(Category category) {
        return modelMapper.map(category, CategoryResponseDTO.class);
    }
}
