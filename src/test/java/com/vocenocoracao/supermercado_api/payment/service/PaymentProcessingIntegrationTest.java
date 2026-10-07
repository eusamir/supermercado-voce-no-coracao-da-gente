package com.vocenocoracao.supermercado_api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.cart.service.impl.CartServiceImpl;
import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderFulfillmentService;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.order.service.impl.OrderFulfillmentServiceImpl;
import com.vocenocoracao.supermercado_api.order.service.impl.OrderServiceImpl;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.gateway.FakePaymentGateway;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGateway;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGatewayProperties;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.payment.service.impl.PaymentServiceImpl;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        PaymentServiceImpl.class,
        OrderFulfillmentServiceImpl.class,
        PaymentProcessingIntegrationTest.InfrastructureConfig.class
})
class PaymentProcessingIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class InfrastructureConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }

        @Bean
        PaymentGateway paymentGateway() {
            return new FakePaymentGateway(new PaymentGatewayProperties(new BigDecimal("1000.00")));
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderFulfillmentService orderFulfillmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User newUser() {
        User user = new User();
        user.setKeycloakId(UUID.randomUUID());
        user.setName("Marta Oliveira");
        user.setEmail(UUID.randomUUID() + "@example.com");
        return userRepository.saveAndFlush(user);
    }

    private Product product(String name) {
        return productRepository.findAllVisible(
                new ProductFilterDTO(name, null, null),
                PageRequest.of(0, 1, Sort.by("name"))
        ).getFirst();
    }

    private int stockOf(String name) {
        return product(name).getStock();
    }

    private void setStock(String name, int stock) {
        Product product = product(name);
        product.setStock(stock);
        productRepository.saveAndFlush(product);
    }

    private UUID checkout(User user, String productName, int quantity) {
        cartService.addItem(user, product(productName).getId(), quantity);
        return orderService.checkout(user).order().getId();
    }

    private Order order(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private Payment payment(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    @Test
    void approvedPaymentThenFulfillmentMakesTheOrderPaidAndDecrementsTheStock() {
        int bananaBefore = stockOf("banana");
        UUID orderId = checkout(newUser(), "banana", 3);

        paymentService.process(orderId);

        assertThat(payment(orderId).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment(orderId).getTransactionId()).startsWith("TXN-");
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(stockOf("banana")).isEqualTo(bananaBefore);

        orderFulfillmentService.fulfill(orderId);

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment(orderId).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(stockOf("banana")).isEqualTo(bananaBefore - 3);
    }

    @Test
    void declinedPaymentLeavesTheStockUntouchedAndCannotBeFulfilled() {
        int arrozBefore = stockOf("arroz");
        UUID orderId = checkout(newUser(), "arroz", 40);

        paymentService.process(orderId);

        assertThat(payment(orderId).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment(orderId).getFailureReason()).contains("limite");
        assertThat(payment(orderId).getTransactionId()).isNull();
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);

        orderFulfillmentService.fulfill(orderId);

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        assertThat(stockOf("arroz")).isEqualTo(arrozBefore);
    }

    @Test
    void processingTheSamePaymentTwiceChargesOnlyOnce() {
        UUID orderId = checkout(newUser(), "banana", 1);

        paymentService.process(orderId);
        String firstTransaction = payment(orderId).getTransactionId();
        paymentService.process(orderId);

        assertThat(payment(orderId).getTransactionId()).isEqualTo(firstTransaction);
    }

    @Test
    void fulfillingTheSameOrderTwiceDecrementsTheStockOnlyOnce() {
        int bananaBefore = stockOf("banana");
        UUID orderId = checkout(newUser(), "banana", 2);
        paymentService.process(orderId);

        orderFulfillmentService.fulfill(orderId);
        orderFulfillmentService.fulfill(orderId);
        orderFulfillmentService.fulfill(orderId);

        assertThat(stockOf("banana")).isEqualTo(bananaBefore - 2);
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void fulfillingBeforeThePaymentIsApprovedDoesNothing() {
        int bananaBefore = stockOf("banana");
        UUID orderId = checkout(newUser(), "banana", 2);

        orderFulfillmentService.fulfill(orderId);

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(stockOf("banana")).isEqualTo(bananaBefore);
    }

    @Test
    void twoCustomersCompetingForTheLastStockOneIsPaidAndTheOtherIsCancelledAndRefunded() {
        int original = stockOf("cenoura (kg)");
        UUID firstOrder = checkout(newUser(), "cenoura (kg)", 2);
        UUID secondOrder = checkout(newUser(), "cenoura (kg)", 2);

        try {
            paymentService.process(firstOrder);
            paymentService.process(secondOrder);
            orderFulfillmentService.fulfill(firstOrder);
            orderFulfillmentService.fulfill(secondOrder);

            assertThat(order(firstOrder).getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order(secondOrder).getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(payment(secondOrder).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment(secondOrder).getFailureReason())
                    .isEqualTo("Estoque insuficiente para: Cenoura (kg) (disponível: 1).");
            assertThat(stockOf("cenoura (kg)")).isEqualTo(original - 2);
        } finally {
            setStock("cenoura (kg)", original);
        }
    }

    @Test
    void concurrentFulfillmentsNeverOversellTheStock() throws Exception {
        int original = stockOf("cenoura (kg)");
        setStock("cenoura (kg)", 2);
        List<UUID> orders = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            orders.add(checkout(newUser(), "cenoura (kg)", 1));
        }
        orders.forEach(paymentService::process);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startSignal = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (UUID orderId : orders) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    orderFulfillmentService.fulfill(orderId);
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            long paid = orders.stream().filter(orderId -> order(orderId).getStatus() == OrderStatus.PAID).count();
            long cancelled = orders.stream().filter(orderId -> order(orderId).getStatus() == OrderStatus.CANCELLED).count();

            assertThat(paid).isEqualTo(2);
            assertThat(cancelled).isEqualTo(2);
            assertThat(stockOf("cenoura (kg)")).isZero();
        } finally {
            executor.shutdownNow();
            setStock("cenoura (kg)", original);
        }
    }

    @Test
    void concurrentDuplicateFulfillmentsOfTheSameOrderDecrementOnce() throws Exception {
        int bananaBefore = stockOf("banana");
        UUID orderId = checkout(newUser(), "banana", 2);
        paymentService.process(orderId);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startSignal = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    orderFulfillmentService.fulfill(orderId);
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(stockOf("banana")).isEqualTo(bananaBefore - 2);
            assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAID);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processingAnUnknownOrderIsReported() {
        assertThatThrownBy(() -> paymentService.process(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> orderFulfillmentService.fulfill(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void stalledQueriesFindPendingAndApprovedPaymentsOlderThanTheThreshold() {
        UUID pendingOrder = checkout(newUser(), "banana", 1);
        UUID approvedOrder = checkout(newUser(), "banana", 1);
        paymentService.process(approvedOrder);

        Instant future = Instant.now().plusSeconds(60);
        Instant past = Instant.now().minusSeconds(3600);
        PageRequest batch = PageRequest.of(0, 100);

        assertThat(paymentRepository.findStalledPending(future, batch))
                .extracting(payment -> payment.getOrder().getId()).contains(pendingOrder).doesNotContain(approvedOrder);
        assertThat(paymentRepository.findStalledApproved(future, batch))
                .extracting(payment -> payment.getOrder().getId()).contains(approvedOrder).doesNotContain(pendingOrder);
        assertThat(paymentRepository.findStalledPending(past, batch)).extracting(payment -> payment.getOrder().getId())
                .doesNotContain(pendingOrder);
        assertThat(paymentRepository.findStalledApproved(past, batch)).extracting(payment -> payment.getOrder().getId())
                .doesNotContain(approvedOrder);

        orderFulfillmentService.fulfill(approvedOrder);

        assertThat(paymentRepository.findStalledApproved(future, batch))
                .extracting(payment -> payment.getOrder().getId()).doesNotContain(approvedOrder);
    }
}
