package com.vocenocoracao.supermercado_api.product.service.impl;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.config.CacheConfig;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    private static final String NOT_FOUND = "Produto não encontrado.";
    private static final String INACTIVE_CATEGORY = "Não é possível usar uma categoria inativa.";
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "price");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductServiceImpl(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public Product create(Product product, UUID categoryId) {
        product.setCategory(findUsableCategory(categoryId));
        return productRepository.saveAndFlush(product);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public Product update(Product product, UUID categoryId) {
        if (!product.getCategory().getId().equals(categoryId)) {
            product.setCategory(findUsableCategory(categoryId));
        }

        Product saved = productRepository.saveAndFlush(product);
        return getOrThrow(saved.getId());
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public Product changeActive(UUID id, boolean active) {
        Product product = getOrThrow(id);

        if (product.isActive() != active) {
            product.setActive(active);
            product = productRepository.saveAndFlush(product);
        }

        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(UUID id) {
        return getOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findActiveById(UUID id) {
        return productRepository.findVisibleById(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> findAll(ProductFilterDTO filter, Pageable pageable) {
        Pageable validated = withValidatedSort(pageable);
        List<Product> content = productRepository.findAll(filter, validated);

        return new PageImpl<>(content, validated, productRepository.count(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> findAllActive(ProductFilterDTO filter, Pageable pageable) {
        Pageable validated = withValidatedSort(pageable);
        List<Product> content = productRepository.findAllVisible(filter, validated);

        return new PageImpl<>(content, validated, productRepository.countVisible(filter));
    }

    private Product getOrThrow(UUID id) {
        return productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private Category findUsableCategory(UUID categoryId) {
        Category category = categoryService.findById(categoryId);

        if (!category.isActive()) {
            throw new InvalidRequestException(INACTIVE_CATEGORY);
        }

        return category;
    }

    private Pageable withValidatedSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        }

        pageable.getSort().forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Ordenação inválida. Campos permitidos: " + String.join(", ", SORTABLE_PROPERTIES.stream().sorted().toList()) + ".");
            }
        });

        return pageable;
    }
}
