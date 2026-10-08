package com.vocenocoracao.supermercado_api.product.controller;

import com.vocenocoracao.supermercado_api.config.CacheConfig;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductPageDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import java.util.List;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogReader {

    private final ProductService productService;
    private final ModelMapper modelMapper;

    public ProductCatalogReader(ProductService productService, ModelMapper modelMapper) {
        this.productService = productService;
        this.modelMapper = modelMapper;
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCT_LIST)
    public ProductPageDTO findPage(ProductFilterDTO filter, Pageable pageable) {
        Page<Product> page = productService.findAllActive(filter, pageable);
        List<ProductResponseDTO> content = page.getContent().stream()
                .map(product -> modelMapper.map(product, ProductResponseDTO.class))
                .toList();

        return new ProductPageDTO(content, page.getTotalElements());
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL)
    public ProductResponseDTO findById(UUID id) {
        return modelMapper.map(productService.findActiveById(id), ProductResponseDTO.class);
    }
}
