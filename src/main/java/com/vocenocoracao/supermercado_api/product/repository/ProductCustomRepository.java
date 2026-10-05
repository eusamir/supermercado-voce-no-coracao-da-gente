package com.vocenocoracao.supermercado_api.product.repository;

import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ProductCustomRepository {
    List<Product> findAllVisible(ProductFilterDTO filter, Pageable pageable);

    long countVisible(ProductFilterDTO filter);

    List<Product> findAll(ProductFilterDTO filter, Pageable pageable);

    long count(ProductFilterDTO filter);
}
