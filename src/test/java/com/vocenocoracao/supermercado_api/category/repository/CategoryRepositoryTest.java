package com.vocenocoracao.supermercado_api.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, CategoryRepositoryTest.PostgresConfig.class})
class CategoryRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    @Autowired
    private CategoryRepository repository;

    private static final CategoryFilterDTO NO_FILTER = new CategoryFilterDTO(null, null, null, null);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    private List<String> names(CategoryFilterDTO filter) {
        return repository.findAll(filter, FIRST_PAGE).stream().map(Category::getName).toList();
    }

    @Test
    void seedHasFiveCategoriesOrderedByName() {
        assertThat(names(NO_FILTER)).containsExactly("Açougue", "Hortifrúti", "Limpeza", "Mercearia", "Padaria");
        assertThat(repository.count(NO_FILTER)).isEqualTo(5);
    }

    @Test
    void searchIsCaseInsensitiveAndPartial() {
        assertThat(names(new CategoryFilterDTO("PAD", null, null, null))).containsExactly("Padaria");
    }

    @Test
    void searchTreatsLikeWildcardsAsLiterals() {
        assertThat(names(new CategoryFilterDTO("%", null, null, null))).isEmpty();
        assertThat(names(new CategoryFilterDTO("_", null, null, null))).isEmpty();
    }

    @Test
    void activeFilterSeparatesActiveFromInactive() {
        Category padaria = repository.findByNameIgnoreCase("padaria").orElseThrow();
        padaria.setActive(false);
        repository.saveAndFlush(padaria);

        assertThat(names(new CategoryFilterDTO(null, true, null, null))).hasSize(4).doesNotContain("Padaria");
        assertThat(names(new CategoryFilterDTO(null, false, null, null))).containsExactly("Padaria");
        assertThat(repository.count(new CategoryFilterDTO(null, true, null, null))).isEqualTo(4);
    }

    @Test
    void createdAtRangeIsInclusiveOfTheWholeEndDay() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(names(new CategoryFilterDTO(null, null, today, today))).hasSize(5);
        assertThat(names(new CategoryFilterDTO(null, null, today.plusDays(1), null))).isEmpty();
        assertThat(names(new CategoryFilterDTO(null, null, null, today.minusDays(1)))).isEmpty();
    }

    @Test
    void paginationAndTotalAreIndependent() {
        var page = repository.findAll(NO_FILTER, PageRequest.of(1, 2));

        assertThat(page).hasSize(2);
        assertThat(repository.count(NO_FILTER)).isEqualTo(5);
    }

    @Test
    void findByNameIgnoreCase() {
        assertThat(repository.findByNameIgnoreCase("hortifrúti")).isPresent();
        assertThat(repository.findByNameIgnoreCase("inexistente")).isEmpty();
    }
}
