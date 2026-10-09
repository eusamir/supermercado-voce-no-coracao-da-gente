package com.vocenocoracao.supermercado_api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.common.BusinessMetrics;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.event.PaymentApprovedEvent;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGateway;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGatewayResult;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.payment.service.impl.PaymentServiceImpl;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private PaymentServiceImpl service;

    private User user;
    private Cart cart;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());

        cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUser(user);

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("49.87"));
    }

    private Product product() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        return product;
    }

    private OrderItem orderItem(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private void stubDecline() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("Pagamento recusado: valor acima do limite."));
    }

    private void stubLoading() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void approvedChargeMarksThePaymentAndPublishesTheApprovedEvent() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.approved("TXN-1"));

        service.process(order.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo("TXN-1");
        assertThat(payment.getFailureReason()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publishEvent(new PaymentApprovedEvent(payment.getId(), order.getId()));
    }

    @Test
    void declinedChargeMarksThePaymentAndTheOrderWithoutPublishingAnything() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("Pagamento recusado: valor acima do limite."));

        service.process(order.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getFailureReason()).isEqualTo("Pagamento recusado: valor acima do limite.");
        assertThat(payment.getTransactionId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        verify(orderRepository).save(order);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDeclinedChargeGivesTheOrderItemsBackToTheCart() {
        Product banana = product();
        Product arroz = product();
        stubDecline();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(orderItemRepository.findAllByOrderId(order.getId()))
                .thenReturn(List.of(orderItem(banana, 3), orderItem(arroz, 1)));
        when(cartItemRepository.findByCartIdAndProductId(any(), any())).thenReturn(Optional.empty());

        service.process(order.getId());

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CartItem::getProduct).containsExactly(banana, arroz);
        assertThat(saved.getAllValues()).extracting(CartItem::getQuantity).containsExactly(3, 1);
        assertThat(saved.getAllValues()).extracting(CartItem::getCart).containsOnly(cart);
    }

    @Test
    void aDeclinedChargeSumsTheQuantityWhenTheProductIsAlreadyInTheCart() {
        Product banana = product();
        CartItem existing = new CartItem();
        existing.setCart(cart);
        existing.setProduct(banana);
        existing.setQuantity(2);
        stubDecline();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(orderItem(banana, 3)));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), banana.getId()))
                .thenReturn(Optional.of(existing));

        service.process(order.getId());

        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(existing);
    }

    @Test
    void anApprovedChargeDoesNotTouchTheCart() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.approved("TXN-1"));

        service.process(order.getId());

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void declinedReasonLongerThanTheColumnIsTruncated() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("x".repeat(400)));

        service.process(order.getId());

        assertThat(payment.getFailureReason()).hasSize(255);
    }

    @Test
    void anAlreadyProcessedPaymentIsIgnoredWithoutChargingAgain() {
        stubLoading();
        payment.setStatus(PaymentStatus.APPROVED);

        service.process(order.getId());

        verify(paymentGateway, never()).charge(any(), any());
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDeclinedPaymentIsNotChargedAgainOnRedelivery() {
        stubLoading();
        payment.setStatus(PaymentStatus.DECLINED);
        order.setStatus(OrderStatus.PAYMENT_DECLINED);

        service.process(order.getId());

        verify(paymentGateway, never()).charge(any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
    }

    @Test
    void unknownOrderIsReportedSoTheMessageCanGoToTheDeadLetterQueue() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderWithoutPaymentIsReported() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(order.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anApprovedChargeIsCountedAsApproved() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.approved("TXN-1"));

        service.process(order.getId());

        verify(businessMetrics).paymentProcessed(true);
    }

    @Test
    void aDeclinedChargeIsCountedAsDeclined() {
        stubLoading();
        when(paymentGateway.charge(order.getId(), payment.getAmount()))
                .thenReturn(PaymentGatewayResult.declined("recusado"));

        service.process(order.getId());

        verify(businessMetrics).paymentProcessed(false);
    }

    @Test
    void anAlreadyProcessedPaymentIsNotCountedAgain() {
        stubLoading();
        payment.setStatus(PaymentStatus.APPROVED);

        service.process(order.getId());

        verify(businessMetrics, never()).paymentProcessed(anyBoolean());
    }
}
