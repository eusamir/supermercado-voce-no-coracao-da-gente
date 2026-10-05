package com.vocenocoracao.supermercado_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.repository.CategoryRepository;
import com.vocenocoracao.supermercado_api.category.service.impl.CategoryServiceImpl;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.impl.ProductServiceImpl;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaConfig.class,
        ProductServiceImpl.class,
        CategoryServiceImpl.class,
        ProductServiceIntegrationTest.PostgresConfig.class
})
class ProductServiceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID categoryId(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseThrow().getId();
    }

    private Product newProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Produto de teste");
        product.setPrice(new BigDecimal("10.50"));
        product.setStock(5);
        product.setActive(true);
        return product;
    }

    @Test
    void createdProductIsReadableWithItsCategoryOutsideTheTransaction() {
        Product created = productService.create(newProduct("Produto integração criar"), categoryId("Padaria"));

        Product found = productService.findById(created.getId());

        assertThat(found.getCategory().getName()).isEqualTo("Padaria");
        assertThat(found.getVersion()).isNotNull();
    }

    @Test
    void updateChangingTheCategoryReturnsTheNewCategoryLoaded() {
        Product created = productService.create(newProduct("Produto integração mover"), categoryId("Padaria"));
        Product loaded = productService.findById(created.getId());
        loaded.setName("Produto integração movido");
        loaded.setStock(9);

        Product updated = productService.update(loaded, categoryId("Limpeza"));

        assertThat(updated.getCategory().getName()).isEqualTo("Limpeza");
        assertThat(updated.getName()).isEqualTo("Produto integração movido");
        assertThat(productService.findById(created.getId()).getStock()).isEqualTo(9);
    }

    @Test
    void changeActiveHidesAndShowsTheProductInThePublicQueries() {
        Product created = productService.create(newProduct("Produto integração ativo"), categoryId("Padaria"));
        ProductFilterDTO filter = new ProductFilterDTO("integração ativo", null, null);

        productService.changeActive(created.getId(), false);
        assertThat(productService.findAllActive(filter, PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(productService.findAll(filter, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        productService.changeActive(created.getId(), true);
        assertThat(productService.findAllActive(filter, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void staleUpdateIsRejectedByOptimisticLocking() {
        Product created = productService.create(newProduct("Produto integração versão"), categoryId("Padaria"));
        Product firstCopy = productService.findById(created.getId());
        Product staleCopy = productService.findById(created.getId());

        firstCopy.setStock(1);
        productService.update(firstCopy, categoryId("Padaria"));

        staleCopy.setStock(2);
        assertThatThrownBy(() -> productService.update(staleCopy, categoryId("Padaria")))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void inactiveCategoryCannotReceiveProducts() {
        Category limpeza = categoryRepository.findByNameIgnoreCase("Limpeza").orElseThrow();
        limpeza.setActive(false);
        categoryRepository.saveAndFlush(limpeza);

        try {
            assertThatThrownBy(() -> productService.create(newProduct("Produto integração inativa"), limpeza.getId()))
                    .hasMessageContaining("categoria inativa");
        } finally {
            limpeza.setActive(true);
            categoryRepository.saveAndFlush(limpeza);
        }
    }
}
