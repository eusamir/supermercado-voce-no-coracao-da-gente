package com.vocenocoracao.supermercado_api.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.repository.CategoryRepository;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ProductRepositoryTest.PostgresConfig.class})
class ProductRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    private static final int SEEDED_PRODUCTS = 27;
    private static final Pageable ALL = PageRequest.of(0, 100, Sort.by("name"));

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private ProductFilterDTO filter(String search, UUID categoryId) {
        return new ProductFilterDTO(search, categoryId, null);
    }

    private List<String> names(ProductFilterDTO filter) {
        return productRepository.findAllVisible(filter, ALL).stream().map(Product::getName).toList();
    }

    private Category category(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    @Test
    void listsAllSeededProductsOrderedByName() {
        List<String> names = names(filter(null, null));

        assertThat(names).hasSize(SEEDED_PRODUCTS).isSorted();
        assertThat(productRepository.countVisible(filter(null, null))).isEqualTo(SEEDED_PRODUCTS);
    }

    @Test
    void loadsTheCategoryWithoutLazyInitialization() {
        Product product = productRepository.findAllVisible(filter("banana", null), ALL).getFirst();

        assertThat(product.getCategory().getName()).isEqualTo("Hortifrúti");
    }

    @Test
    void searchIsCaseInsensitiveAndPartial() {
        assertThat(names(filter("LEITE", null))).containsExactly("Leite desnatado 1L", "Leite integral 1L");
    }

    @Test
    void searchTreatsLikeWildcardsAsLiterals() {
        assertThat(names(filter("%", null))).isEmpty();
        assertThat(names(filter("_", null))).isEmpty();
    }

    @Test
    void filtersByCategory() {
        UUID padaria = category("Padaria").getId();

        assertThat(names(filter(null, padaria))).hasSize(5);
        assertThat(productRepository.countVisible(filter(null, padaria))).isEqualTo(5);
    }

    @Test
    void combinesSearchAndCategory() {
        UUID mercearia = category("Mercearia").getId();
        UUID limpeza = category("Limpeza").getId();

        assertThat(names(filter("leite", mercearia))).hasSize(2);
        assertThat(names(filter("leite", limpeza))).isEmpty();
    }

    @Test
    void hidesInactiveProducts() {
        Product product = productRepository.findAllVisible(filter("banana", null), ALL).getFirst();
        product.setActive(false);
        productRepository.saveAndFlush(product);

        assertThat(names(filter("banana", null))).isEmpty();
        assertThat(productRepository.countVisible(filter(null, null))).isEqualTo(SEEDED_PRODUCTS - 1);
        assertThat(productRepository.findVisibleById(product.getId())).isEmpty();
    }

    @Test
    void hidesProductsOfInactiveCategories() {
        Category padaria = category("Padaria");
        padaria.setActive(false);
        categoryRepository.saveAndFlush(padaria);

        assertThat(names(filter(null, padaria.getId()))).isEmpty();
        assertThat(productRepository.countVisible(filter(null, null))).isEqualTo(SEEDED_PRODUCTS - 5);
    }

    @Test
    void sortsByPriceDescending() {
        Pageable byPrice = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "price"));

        List<BigDecimal> prices = productRepository.findAllVisible(filter(null, null), byPrice)
                .stream().map(Product::getPrice).toList();

        assertThat(prices).isSortedAccordingTo((first, second) -> second.compareTo(first));
        assertThat(prices.getFirst()).isEqualByComparingTo("39.90");
    }

    @Test
    void paginationIsIndependentFromTheTotal() {
        List<Product> page = productRepository.findAllVisible(filter(null, null), PageRequest.of(2, 10, Sort.by("name")));

        assertThat(page).hasSize(7);
    }

    @Test
    void findVisibleByIdReturnsProductWithCategory() {
        UUID id = productRepository.findAllVisible(filter("arroz", null), ALL).getFirst().getId();

        assertThat(productRepository.findVisibleById(id)).hasValueSatisfying(
                product -> assertThat(product.getCategory().getName()).isEqualTo("Mercearia"));
    }

    @Test
    void administrativeListingIncludesInactiveProductsAndInactiveCategories() {
        Category padaria = category("Padaria");
        padaria.setActive(false);
        categoryRepository.saveAndFlush(padaria);
        Product banana = productRepository.findAllVisible(filter("banana", null), ALL).getFirst();
        banana.setActive(false);
        productRepository.saveAndFlush(banana);

        assertThat(productRepository.count(filter(null, null))).isEqualTo(SEEDED_PRODUCTS);
        assertThat(productRepository.findAll(filter(null, padaria.getId()), ALL)).hasSize(5);
        assertThat(productRepository.findAll(filter("banana", null), ALL)).hasSize(1);
    }

    @Test
    void administrativeListingFiltersByActiveStatus() {
        Product banana = productRepository.findAllVisible(filter("banana", null), ALL).getFirst();
        banana.setActive(false);
        productRepository.saveAndFlush(banana);

        ProductFilterDTO onlyInactive = new ProductFilterDTO(null, null, false);
        ProductFilterDTO onlyActive = new ProductFilterDTO(null, null, true);

        assertThat(productRepository.findAll(onlyInactive, ALL)).extracting(Product::getName)
                .containsExactly("Banana prata (kg)");
        assertThat(productRepository.count(onlyInactive)).isEqualTo(1);
        assertThat(productRepository.count(onlyActive)).isEqualTo(SEEDED_PRODUCTS - 1);
    }

    @Test
    void publicListingIgnoresTheActiveFilter() {
        ProductFilterDTO onlyInactive = new ProductFilterDTO(null, null, false);

        assertThat(productRepository.countVisible(onlyInactive)).isEqualTo(SEEDED_PRODUCTS);
    }

    @Test
    void findByIdWithCategoryReturnsHiddenProducts() {
        Product banana = productRepository.findAllVisible(filter("banana", null), ALL).getFirst();
        banana.setActive(false);
        productRepository.saveAndFlush(banana);

        assertThat(productRepository.findByIdWithCategory(banana.getId())).isPresent();
        assertThat(productRepository.findVisibleById(banana.getId())).isEmpty();
    }
}
