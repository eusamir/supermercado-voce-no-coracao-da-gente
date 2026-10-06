package com.vocenocoracao.supermercado_api.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cart.service.impl.CartServiceImpl;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaConfig.class,
        CartServiceImpl.class,
        CartServiceIntegrationTest.PostgresConfig.class
})
class CartServiceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User newUser = new User();
        newUser.setKeycloakId(UUID.randomUUID());
        newUser.setName("Marta Oliveira");
        newUser.setEmail(UUID.randomUUID() + "@example.com");
        user = userRepository.saveAndFlush(newUser);
    }

    private Product product(String name) {
        return productRepository.findAllVisible(
                new com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO(name, null, null),
                PageRequest.of(0, 1, Sort.by("name"))
        ).getFirst();
    }

    private CartItem itemOf(CartDetails details, UUID productId) {
        return details.items().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void viewOfAUserWithoutCartIsEmptyAndCreatesNothing() {
        CartDetails details = cartService.getCart(user);

        assertThat(details.cart()).isNull();
        assertThat(details.items()).isEmpty();
        assertThat(cartRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void addItemCreatesTheCartAndTheItemWithTheProductLoaded() {
        Product banana = product("banana");

        CartDetails details = cartService.addItem(user, banana.getId(), 2);

        assertThat(details.cart().getId()).isNotNull();
        CartItem item = itemOf(details, banana.getId());
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getProduct().getName()).isEqualTo("Banana prata (kg)");
        assertThat(item.getProduct().getCategory().getName()).isEqualTo("Hortifrúti");
    }

    @Test
    void addingTheSameProductTwiceSumsTheQuantityInASingleItem() {
        Product banana = product("banana");

        cartService.addItem(user, banana.getId(), 2);
        CartDetails details = cartService.addItem(user, banana.getId(), 3);

        assertThat(details.items()).hasSize(1);
        assertThat(itemOf(details, banana.getId()).getQuantity()).isEqualTo(5);
    }

    @Test
    void eachUserHasAnIndependentCart() {
        Product banana = product("banana");
        User other = new User();
        other.setKeycloakId(UUID.randomUUID());
        other.setName("Pedro Oliveira");
        other.setEmail(UUID.randomUUID() + "@example.com");
        other = userRepository.saveAndFlush(other);

        cartService.addItem(user, banana.getId(), 2);

        assertThat(cartService.getCart(other).items()).isEmpty();
        assertThat(cartService.getCart(user).items()).hasSize(1);
    }

    @Test
    void stockIsValidatedAgainstTheSumAndNothingIsChangedOnFailure() {
        Product cenoura = product("cenoura (kg)");

        cartService.addItem(user, cenoura.getId(), 2);

        assertThatThrownBy(() -> cartService.addItem(user, cenoura.getId(), 2))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Estoque insuficiente. Disponível: 3.");
        assertThat(itemOf(cartService.getCart(user), cenoura.getId()).getQuantity()).isEqualTo(2);
    }

    @Test
    void productWithoutStockCannotBeAdded() {
        Product costela = product("costela");

        assertThatThrownBy(() -> cartService.addItem(user, costela.getId(), 1))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(cartService.getCart(user).items()).isEmpty();
    }

    @Test
    void inactiveProductCannotBeAdded() {
        Product leite = product("leite desnatado");
        UUID leiteId = leite.getId();
        leite.setActive(false);
        Product deactivated = productRepository.saveAndFlush(leite);

        try {
            assertThatThrownBy(() -> cartService.addItem(user, leiteId, 1))
                    .isInstanceOf(NotFoundException.class);
        } finally {
            deactivated.setActive(true);
            productRepository.saveAndFlush(deactivated);
        }
    }

    @Test
    void updateItemChangesTheQuantityAndZeroRemovesIt() {
        Product banana = product("banana");
        cartService.addItem(user, banana.getId(), 2);

        CartDetails updated = cartService.updateItem(user, banana.getId(), 7);
        assertThat(itemOf(updated, banana.getId()).getQuantity()).isEqualTo(7);

        CartDetails emptied = cartService.updateItem(user, banana.getId(), 0);
        assertThat(emptied.items()).isEmpty();
    }

    @Test
    void removeItemKeepsTheOtherItems() {
        Product banana = product("banana");
        Product arroz = product("arroz");
        cartService.addItem(user, banana.getId(), 1);
        cartService.addItem(user, arroz.getId(), 1);

        CartDetails details = cartService.removeItem(user, banana.getId());

        List<UUID> remaining = details.items().stream().map(item -> item.getProduct().getId()).toList();
        assertThat(remaining).containsExactly(arroz.getId());
    }

    @Test
    void removeItemFailsWhenItIsNotInTheCart() {
        Product banana = product("banana");
        cartService.addItem(user, product("arroz").getId(), 1);

        assertThatThrownBy(() -> cartService.removeItem(user, banana.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void itemsComeInTheOrderTheyWereAdded() {
        Product banana = product("banana");
        Product arroz = product("arroz");
        Product leite = product("leite integral");
        cartService.addItem(user, leite.getId(), 1);
        cartService.addItem(user, banana.getId(), 1);
        cartService.addItem(user, arroz.getId(), 1);

        List<UUID> ordered = cartService.getCart(user).items().stream()
                .map(item -> item.getProduct().getId()).toList();

        assertThat(ordered).containsExactly(leite.getId(), banana.getId(), arroz.getId());
        assertThat(product("banana").getPrice()).isEqualByComparingTo(new BigDecimal("6.99"));
    }
}
