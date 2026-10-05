package com.vocenocoracao.supermercado_api.product.service;

import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Product create(Product product, UUID categoryId);

    Product update(Product product, UUID categoryId);

    Product changeActive(UUID id, boolean active);

    Product findById(UUID id);

    Product findActiveById(UUID id);

    Page<Product> findAll(ProductFilterDTO filter, Pageable pageable);

    Page<Product> findAllActive(ProductFilterDTO filter, Pageable pageable);
}
