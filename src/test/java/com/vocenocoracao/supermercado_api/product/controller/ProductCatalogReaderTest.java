package com.vocenocoracao.supermercado_api.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.controller.converter.ProductToProductResponseDTOConverter;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductPageDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductCatalogReaderTest {

    @Mock
    private ProductService productService;

    private ProductCatalogReader reader;
    private Category category;

    @BeforeEach
    void setUp() {
        reader = new ProductCatalogReader(
                productService,
                new ModelMapperConfig().modelMapper(List.of(new ProductToProductResponseDTOConverter()))
        );

        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Mercearia");
        category.setActive(true);
    }

    private Product product(String name) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setDescription("Descrição");
        product.setPrice(new BigDecimal("5.29"));
        product.setStock(200);
        product.setActive(true);
        product.setCategory(category);
        return product;
    }

    @Test
    void findPageConvertsTheEntitiesAndKeepsTheTotalOfTheWholeResult() {
        Pageable pageable = PageRequest.of(2, 2);
        when(productService.findAllActive(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product("Leite integral 1L")), pageable, 5));

        ProductPageDTO page = reader.findPage(new ProductFilterDTO("leite", null, null), pageable);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.content()).hasSize(1);
        ProductResponseDTO item = page.content().getFirst();
        assertThat(item.name()).isEqualTo("Leite integral 1L");
        assertThat(item.price()).isEqualByComparingTo("5.29");
        assertThat(item.stock()).isEqualTo(200);
        assertThat(item.category().name()).isEqualTo("Mercearia");
    }

    @Test
    void findPageDelegatesTheFilterAndThePageableToTheService() {
        Pageable pageable = PageRequest.of(0, 20);
        ProductFilterDTO filter = new ProductFilterDTO("leite", category.getId(), null);
        when(productService.findAllActive(filter, pageable)).thenReturn(new PageImpl<>(List.of()));

        reader.findPage(filter, pageable);

        verify(productService).findAllActive(filter, pageable);
    }

    @Test
    void findByIdConvertsTheEntity() {
        Product product = product("Leite integral 1L");
        when(productService.findActiveById(product.getId())).thenReturn(product);

        ProductResponseDTO response = reader.findById(product.getId());

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo("Leite integral 1L");
    }

    @Test
    void findByIdPropagatesNotFoundSoNothingIsCached() {
        UUID id = UUID.randomUUID();
        when(productService.findActiveById(id)).thenThrow(new NotFoundException("Produto não encontrado."));

        assertThatThrownBy(() -> reader.findById(id)).isInstanceOf(NotFoundException.class);
    }
}
