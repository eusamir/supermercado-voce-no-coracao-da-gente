package com.vocenocoracao.supermercado_api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.common.BusinessMetrics;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.impl.OrderFulfillmentServiceImpl;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private OrderFulfillmentServiceImpl service;

    private Order order;
    private Payment payment;
    private Product banana;
    private Product arroz;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.APPROVED);

        banana = product("Banana prata (kg)", 10);
        arroz = product("Arroz branco 5kg", 5);
    }

    private Product product(String name, int stock) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setStock(stock);
        return product;
    }

    private OrderItem item(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private void stubLoading(List<OrderItem> items, List<Product> products) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(items);
        when(productRepository.findAllByIdForUpdate(any())).thenReturn(products);
    }

    @Test
    void decrementsTheStockOfEveryItemAndMarksTheOrderAsPaid() {
        stubLoading(List.of(item(banana, 3), item(arroz, 5)), List.of(banana, arroz));

        service.fulfill(order.getId());

        assertThat(banana.getStock()).isEqualTo(7);
        assertThat(arroz.getStock()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(productRepository).saveAll(List.of(banana, arroz));
        verify(orderRepository).save(order);
    }

    @Test
    void sumsTheQuantitiesWhenTheSameProductAppearsTwice() {
        stubLoading(List.of(item(banana, 3), item(banana, 4)), List.of(banana));

        service.fulfill(order.getId());

        assertThat(banana.getStock()).isEqualTo(3);
    }

    @Test
    void locksTheProductsOfTheOrderWithoutDuplicates() {
        stubLoading(List.of(item(banana, 1), item(banana, 1)), List.of(banana));

        service.fulfill(order.getId());

        verify(productRepository).findAllByIdForUpdate(Set.of(banana.getId()));
    }

    @Test
    void insufficientStockCancelsTheOrderRefundsThePaymentAndTouchesNoStock() {
        stubLoading(List.of(item(banana, 3), item(arroz, 6)), List.of(banana, arroz));

        service.fulfill(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getFailureReason())
                .isEqualTo("Estoque insuficiente para: Arroz branco 5kg (disponível: 5).");
        assertThat(banana.getStock()).isEqualTo(10);
        assertThat(arroz.getStock()).isEqualTo(5);
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void aSecondDeliveryOfAPaidOrderDoesNotDecrementAgain() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        service.fulfill(order.getId());

        verify(productRepository, never()).findAllByIdForUpdate(any());
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void aCancelledOrderIsNotProcessedAgain() {
        order.setStatus(OrderStatus.CANCELLED);
        payment.setStatus(PaymentStatus.REFUNDED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        service.fulfill(order.getId());

        verify(productRepository, never()).findAllByIdForUpdate(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void doesNothingWhileThePaymentIsNotApproved() {
        payment.setStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        service.fulfill(order.getId());

        verify(productRepository, never()).findAllByIdForUpdate(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void doesNothingForADeclinedPayment() {
        payment.setStatus(PaymentStatus.DECLINED);
        order.setStatus(OrderStatus.PAYMENT_DECLINED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        service.fulfill(order.getId());

        verify(productRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    void unknownOrderIsReportedSoTheMessageCanGoToTheDeadLetterQueue() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fulfill(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aPaidOrderIsCounted() {
        stubLoading(List.of(item(banana, 3)), List.of(banana));

        service.fulfill(order.getId());

        verify(businessMetrics).orderFulfilled(true);
    }

    @Test
    void aCancelledOrderIsCounted() {
        stubLoading(List.of(item(arroz, 6)), List.of(arroz));

        service.fulfill(order.getId());

        verify(businessMetrics).orderFulfilled(false);
    }

    @Test
    void aDuplicatedDeliveryIsNotCounted() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));

        service.fulfill(order.getId());

        verify(businessMetrics, never()).orderFulfilled(anyBoolean());
    }
}
