package com.vocenocoracao.supermercado_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.product.service.impl.ProductServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private static final ProductFilterDTO NO_FILTER = new ProductFilterDTO(null, null, null);

    @Mock
    private ProductRepository repository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ProductServiceImpl service;

    @Test
    void findAllActiveDefaultsToNameAscending() {
        when(repository.findAllVisible(any(), any())).thenReturn(List.of());
        when(repository.countVisible(any())).thenReturn(0L);

        service.findAllActive(NO_FILTER, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllVisible(any(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void findAllActiveAcceptsSortByPrice() {
        when(repository.findAllVisible(any(), any())).thenReturn(List.of());
        when(repository.countVisible(any())).thenReturn(0L);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "price"));

        Page<Product> page = service.findAllActive(NO_FILTER, pageable);

        assertThat(page.getPageable().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "price"));
    }

    @Test
    void findAllActiveRejectsSortOutsideTheAllowedList() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("stock"));

        assertThatThrownBy(() -> service.findAllActive(NO_FILTER, pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("name, price");
        verify(repository, never()).findAllVisible(any(), any());
    }

    @Test
    void findAllActiveReturnsTotalFromTheSeparateCount() {
        when(repository.findAllVisible(any(), any())).thenReturn(List.of(new Product()));
        when(repository.countVisible(any())).thenReturn(42L);

        Page<Product> page = service.findAllActive(NO_FILTER, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(42);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findActiveByIdReturnsVisibleProduct() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        when(repository.findVisibleById(id)).thenReturn(Optional.of(product));

        assertThat(service.findActiveById(id)).isSameAs(product);
    }

    @Test
    void findActiveByIdThrowsWhenNotVisible() {
        UUID id = UUID.randomUUID();
        when(repository.findVisibleById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findActiveById(id)).isInstanceOf(NotFoundException.class);
    }

    private Category category(UUID id, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName("Mercearia");
        category.setActive(active);
        return category;
    }

    private Product product(UUID id, Category category, boolean active) {
        Product product = new Product();
        product.setId(id);
        product.setCategory(category);
        product.setActive(active);
        return product;
    }

    @Test
    void createAssignsTheCategoryAndSaves() {
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, true);
        Product input = new Product();
        when(categoryService.findById(categoryId)).thenReturn(category);
        when(repository.saveAndFlush(input)).thenReturn(input);

        Product created = service.create(input, categoryId);

        assertThat(created.getCategory()).isSameAs(category);
    }

    @Test
    void createRejectsInactiveCategory() {
        UUID categoryId = UUID.randomUUID();
        when(categoryService.findById(categoryId)).thenReturn(category(categoryId, false));

        assertThatThrownBy(() -> service.create(new Product(), categoryId))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createPropagatesCategoryNotFound() {
        UUID categoryId = UUID.randomUUID();
        when(categoryService.findById(categoryId)).thenThrow(new NotFoundException("Categoria não encontrada."));

        assertThatThrownBy(() -> service.create(new Product(), categoryId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateKeepsTheCategoryWhenItDidNotChange() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product input = product(productId, category(categoryId, false), true);
        when(repository.saveAndFlush(input)).thenReturn(input);
        when(repository.findByIdWithCategory(productId)).thenReturn(Optional.of(input));

        service.update(input, categoryId);

        verify(categoryService, never()).findById(any());
    }

    @Test
    void updateChangesTheCategory() {
        UUID oldCategoryId = UUID.randomUUID();
        UUID newCategoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Category newCategory = category(newCategoryId, true);
        Product input = product(productId, category(oldCategoryId, true), true);
        when(categoryService.findById(newCategoryId)).thenReturn(newCategory);
        when(repository.saveAndFlush(input)).thenReturn(input);
        when(repository.findByIdWithCategory(productId)).thenReturn(Optional.of(input));

        assertThat(service.update(input, newCategoryId).getCategory()).isSameAs(newCategory);
    }

    @Test
    void changeActiveDeactivatesProduct() {
        UUID productId = UUID.randomUUID();
        Product existing = product(productId, category(UUID.randomUUID(), true), true);
        when(repository.findByIdWithCategory(productId)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        assertThat(service.changeActive(productId, false).isActive()).isFalse();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void changeActiveIsIdempotent() {
        UUID productId = UUID.randomUUID();
        Product existing = product(productId, category(UUID.randomUUID(), true), false);
        when(repository.findByIdWithCategory(productId)).thenReturn(Optional.of(existing));

        assertThat(service.changeActive(productId, false).isActive()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID productId = UUID.randomUUID();
        when(repository.findByIdWithCategory(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(productId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAllAdministrativeUsesTheUnrestrictedQueryAndValidatesSort() {
        when(repository.findAll(any(ProductFilterDTO.class), any(Pageable.class))).thenReturn(List.of());
        when(repository.count(any(ProductFilterDTO.class))).thenReturn(0L);

        service.findAll(NO_FILTER, PageRequest.of(0, 20));

        verify(repository).findAll(any(ProductFilterDTO.class), any(Pageable.class));
        assertThatThrownBy(() -> service.findAll(NO_FILTER, PageRequest.of(0, 20, Sort.by("stock"))))
                .isInstanceOf(InvalidRequestException.class);
    }
}
