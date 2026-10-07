package com.vocenocoracao.supermercado_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.message.PaymentApprovedMessage;
import com.vocenocoracao.supermercado_api.payment.message.PaymentRequestedMessage;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessagePublisher;
import com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1.1",
        "payment.gateway.max-amount=1000.00",
        "payment.reconciliation.interval-ms=500",
        "payment.reconciliation.stale-after=2s"
})
class PaymentFlowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

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

    @Autowired
    private PaymentMessagePublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    private UUID checkout(String productName, int quantity) {
        User user = newUser();
        cartService.addItem(user, product(productName).getId(), quantity);
        return orderService.checkout(user).order().getId();
    }

    private OrderStatus orderStatus(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private Payment payment(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    private void awaitOrderStatus(UUID orderId, OrderStatus expected) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo(expected));
    }

    @Test
    void checkoutIsPaidAsynchronouslyAndTheStockIsDecremented() {
        int bananaBefore = stockOf("banana");

        UUID orderId = checkout("banana", 3);

        awaitOrderStatus(orderId, OrderStatus.PAID);
        assertThat(payment(orderId).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment(orderId).getTransactionId()).startsWith("TXN-");
        assertThat(stockOf("banana")).isEqualTo(bananaBefore - 3);
    }

    @Test
    void anAmountAboveTheGatewayLimitIsDeclinedAndTheStockIsKept() {
        int arrozBefore = stockOf("arroz");

        UUID orderId = checkout("arroz", 40);

        awaitOrderStatus(orderId, OrderStatus.PAYMENT_DECLINED);
        assertThat(payment(orderId).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment(orderId).getFailureReason()).contains("limite");
        assertThat(stockOf("arroz")).isEqualTo(arrozBefore);
    }

    @Test
    void twoCustomersBuyingTheLastUnitsOneIsPaidAndTheOtherIsCancelledAndRefunded() {
        int original = stockOf("cenoura (kg)");

        try {
            UUID firstOrder = checkout("cenoura (kg)", 2);
            UUID secondOrder = checkout("cenoura (kg)", 2);

            await().atMost(TIMEOUT).untilAsserted(() -> {
                List<OrderStatus> statuses = List.of(orderStatus(firstOrder), orderStatus(secondOrder));
                assertThat(statuses).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.CANCELLED);
            });

            UUID cancelled = orderStatus(firstOrder) == OrderStatus.CANCELLED ? firstOrder : secondOrder;
            assertThat(payment(cancelled).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment(cancelled).getFailureReason()).startsWith("Estoque insuficiente");
            assertThat(stockOf("cenoura (kg)")).isEqualTo(original - 2);
        } finally {
            setStock("cenoura (kg)", original);
        }
    }

    @Test
    void duplicatedMessagesNeitherChargeNorDecrementTwice() {
        int bananaBefore = stockOf("banana");
        UUID orderId = checkout("banana", 2);
        awaitOrderStatus(orderId, OrderStatus.PAID);
        String transactionId = payment(orderId).getTransactionId();
        UUID paymentId = payment(orderId).getId();

        publisher.publishPaymentRequested(new PaymentRequestedMessage(paymentId, orderId));
        publisher.publishPaymentApproved(new PaymentApprovedMessage(paymentId, orderId));
        publisher.publishPaymentApproved(new PaymentApprovedMessage(paymentId, orderId));

        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(stockOf("banana")).isEqualTo(bananaBefore - 2);
            assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
            assertThat(payment(orderId).getTransactionId()).isEqualTo(transactionId);
        });
    }

    @Test
    void aPaymentWhoseMessageWasNeverPublishedIsRecoveredByTheReconciliationJob() {
        int bananaBefore = stockOf("banana");
        Product banana = product("banana");

        Order order = new Order();
        order.setUser(newUser());
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setTotal(new BigDecimal("13.98"));
        order = orderRepository.saveAndFlush(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(banana);
        item.setName(banana.getName());
        item.setUnitPrice(new BigDecimal("6.99"));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("13.98"));
        orderItemRepository.saveAndFlush(item);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotal());
        paymentRepository.saveAndFlush(payment);

        UUID orderId = order.getId();

        awaitOrderStatus(orderId, OrderStatus.PAID);
        assertThat(stockOf("banana")).isEqualTo(bananaBefore - 2);
    }

    @Test
    void aMessageThatKeepsFailingEndsInTheDeadLetterQueue() {
        UUID unknownOrder = UUID.randomUUID();

        publisher.publishPaymentRequested(new PaymentRequestedMessage(UUID.randomUUID(), unknownOrder));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Message dead = rabbitTemplate.receive(PaymentMessaging.DEAD_LETTER_QUEUE, 500);
            assertThat(dead).isNotNull();
            assertThat(new String(dead.getBody())).contains(unknownOrder.toString());
        });
    }
}
