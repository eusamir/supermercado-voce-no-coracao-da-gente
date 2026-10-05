package com.vocenocoracao.supermercado_api.product.controller;

import com.vocenocoracao.supermercado_api.product.dto.ProductActiveDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductRequestDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
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
public class ProductController {
    private final ProductService productService;
    private final ModelMapper modelMapper;

    public ProductController(ProductService productService, ModelMapper modelMapper) {
        this.productService = productService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/api/products")
    public Page<ProductResponseDTO> findAllActive(
            @ParameterObject ProductFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return productService.findAllActive(filter, pageable).map(this::toResponse);
    }

    @GetMapping("/api/products/{id}")
    public ProductResponseDTO findActiveById(@PathVariable UUID id) {
        return toResponse(productService.findActiveById(id));
    }

    @GetMapping("/api/admin/products")
    public Page<ProductResponseDTO> findAll(
            @ParameterObject ProductFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return productService.findAll(filter, pageable).map(this::toResponse);
    }

    @GetMapping("/api/admin/products/{id}")
    public ProductResponseDTO findById(@PathVariable UUID id) {
        return toResponse(productService.findById(id));
    }

    @PostMapping("/api/products")
    public ResponseEntity<ProductResponseDTO> create(@Validated @RequestBody ProductRequestDTO request) {
        Product product = productService.create(modelMapper.map(request, Product.class), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @PutMapping("/api/products/{id}")
    public ProductResponseDTO update(
            @PathVariable UUID id,
            @Validated @RequestBody ProductRequestDTO request
    ) {
        Product product = productService.findById(id);
        modelMapper.map(request, product);
        return toResponse(productService.update(product, request.categoryId()));
    }

    @PatchMapping("/api/products/{id}/active")
    public ProductResponseDTO changeActive(
            @PathVariable UUID id,
            @Validated @RequestBody ProductActiveDTO request
    ) {
        return toResponse(productService.changeActive(id, request.active()));
    }

    private ProductResponseDTO toResponse(Product product) {
        return modelMapper.map(product, ProductResponseDTO.class);
    }
}
