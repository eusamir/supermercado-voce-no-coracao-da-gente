package com.vocenocoracao.supermercado_api.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.cart.service.impl.CartServiceImpl;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.impl.OrderServiceImpl;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        OrderServiceImpl.class,
        CartServiceImpl.class,
        OrderServiceIntegrationTest.PostgresConfig.class
})
class OrderServiceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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
                new ProductFilterDTO(name, null, null),
                PageRequest.of(0, 1, Sort.by("name"))
        ).getFirst();
    }

    @Test
    void checkoutCreatesTheOrderTheItemsAndThePendingPaymentAndEmptiesTheCart() {
        Product banana = product("banana");
        Product arroz = product("arroz");
        cartService.addItem(user, banana.getId(), 3);
        cartService.addItem(user, arroz.getId(), 1);

        OrderDetails details = orderService.checkout(user);

        UUID orderId = details.order().getId();
        assertThat(orderRepository.findById(orderId)).hasValueSatisfying(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(order.getTotal()).isEqualByComparingTo("49.87");
            assertThat(order.getCreatedAt()).isNotNull();
        });
        assertThat(orderItemRepository.findAllByOrderId(orderId)).hasSize(2);
        assertThat(paymentRepository.findByOrderId(orderId)).hasValueSatisfying(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getAmount()).isEqualByComparingTo("49.87");
            assertThat(payment.getTransactionId()).isNull();
        });
        assertThat(cartService.getCart(user).items()).isEmpty();
    }

    @Test
    void checkoutDoesNotDecrementTheStock() {
        Product banana = product("banana");
        int stockBefore = banana.getStock();
        cartService.addItem(user, banana.getId(), 3);

        orderService.checkout(user);

        assertThat(product("banana").getStock()).isEqualTo(stockBefore);
    }

    @Test
    void orderItemsKeepTheSnapshotWhenThePriceLaterChanges() {
        Product leite = product("leite integral");
        cartService.addItem(user, leite.getId(), 2);
        UUID orderId = orderService.checkout(user).order().getId();

        Product current = product("leite integral");
        BigDecimal originalPrice = current.getPrice();
        current.setPrice(new BigDecimal("99.99"));
        current.setName("Leite integral renomeado");
        Product changed = productRepository.saveAndFlush(current);

        try {
            OrderItem snapshot = orderItemRepository.findAllByOrderId(orderId).getFirst();
            assertThat(snapshot.getUnitPrice()).isEqualByComparingTo(originalPrice);
            assertThat(snapshot.getName()).isEqualTo("Leite integral 1L");
            assertThat(snapshot.getSubtotal()).isEqualByComparingTo(originalPrice.multiply(BigDecimal.valueOf(2)));
        } finally {
            changed.setPrice(originalPrice);
            changed.setName("Leite integral 1L");
            productRepository.saveAndFlush(changed);
        }
    }

    @Test
    void checkoutWithAnEmptyCartFails() {
        assertThatThrownBy(() -> orderService.checkout(user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("O carrinho está vazio.");
    }

    @Test
    void secondCheckoutRightAfterTheFirstFindsAnEmptyCart() {
        cartService.addItem(user, product("banana").getId(), 1);
        orderService.checkout(user);

        assertThatThrownBy(() -> orderService.checkout(user)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void checkoutFailsAndKeepsTheCartWhenTheStockDroppedAfterAddingToTheCart() {
        Product cenoura = product("cenoura (kg)");
        int originalStock = cenoura.getStock();
        cartService.addItem(user, cenoura.getId(), 3);

        cenoura.setStock(2);
        Product reduced = productRepository.saveAndFlush(cenoura);

        try {
            assertThatThrownBy(() -> orderService.checkout(user))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("Estoque insuficiente para: Cenoura (kg) (disponível: 2).");
            assertThat(cartService.getCart(user).items()).hasSize(1);
            assertThat(orderRepository.findAll().stream().filter(order -> order.getUser().getId().equals(user.getId())))
                    .isEmpty();
        } finally {
            reduced.setStock(originalStock);
            productRepository.saveAndFlush(reduced);
        }
    }

    @Test
    void checkoutFailsWhenAProductWasDeactivatedAfterAddingToTheCart() {
        Product leite = product("leite desnatado");
        cartService.addItem(user, leite.getId(), 1);
        leite.setActive(false);
        Product deactivated = productRepository.saveAndFlush(leite);

        try {
            assertThatThrownBy(() -> orderService.checkout(user))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Produtos indisponíveis: Leite desnatado 1L.");
            assertThat(cartService.getCart(user).items()).hasSize(1);
        } finally {
            deactivated.setActive(true);
            productRepository.saveAndFlush(deactivated);
        }
    }

    @Test
    void simultaneousCheckoutsOfTheSameCartCreateExactlyOneOrder() throws Exception {
        cartService.addItem(user, product("banana").getId(), 2);

        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Object> attempt = () -> {
            startSignal.await();
            try {
                return orderService.checkout(user);
            } catch (InvalidRequestException exception) {
                return exception;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(attempt);
            Future<Object> second = executor.submit(attempt);
            startSignal.countDown();

            List<Object> results = List.of(first.get(), second.get());

            assertThat(results.stream().filter(OrderDetails.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(InvalidRequestException.class::isInstance)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        long orders = orderRepository.findAll().stream()
                .filter(order -> order.getUser().getId().equals(user.getId()))
                .count();
        assertThat(orders).isEqualTo(1);
    }

    @Test
    void eachOrderGetsItsOwnPayment() {
        cartService.addItem(user, product("banana").getId(), 1);
        UUID firstOrder = orderService.checkout(user).order().getId();
        cartService.addItem(user, product("arroz").getId(), 1);
        UUID secondOrder = orderService.checkout(user).order().getId();

        Payment firstPayment = paymentRepository.findByOrderId(firstOrder).orElseThrow();
        Payment secondPayment = paymentRepository.findByOrderId(secondOrder).orElseThrow();

        assertThat(firstPayment.getId()).isNotEqualTo(secondPayment.getId());
        assertThat(firstPayment.getAmount()).isEqualByComparingTo("6.99");
        assertThat(secondPayment.getAmount()).isEqualByComparingTo("28.90");
    }
}
