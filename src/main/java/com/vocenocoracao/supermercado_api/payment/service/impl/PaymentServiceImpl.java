package com.vocenocoracao.supermercado_api.payment.service.impl;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.common.BusinessMetrics;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.event.PaymentApprovedEvent;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGateway;
import com.vocenocoracao.supermercado_api.payment.gateway.PaymentGatewayResult;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.payment.service.PaymentService;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final BusinessMetrics businessMetrics;

    public PaymentServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            ApplicationEventPublisher eventPublisher,
            BusinessMetrics businessMetrics
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public void process(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado."));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        PaymentGatewayResult result = paymentGateway.charge(orderId, payment.getAmount());

        if (result.approved()) {
            payment.setStatus(PaymentStatus.APPROVED);
            payment.setTransactionId(result.transactionId());
            paymentRepository.save(payment);
            eventPublisher.publishEvent(new PaymentApprovedEvent(payment.getId(), orderId));
            businessMetrics.paymentProcessed(true);
            return;
        }

        payment.setStatus(PaymentStatus.DECLINED);
        payment.setFailureReason(truncate(result.failureReason()));
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAYMENT_DECLINED);
        orderRepository.save(order);
        restoreCart(order);
        businessMetrics.paymentProcessed(false);
    }

    private void restoreCart(Order order) {
        cartRepository.findByUserId(order.getUser().getId()).ifPresent(cart ->
                orderItemRepository.findAllByOrderId(order.getId()).forEach(orderItem -> {
                    CartItem cartItem = cartItemRepository
                            .findByCartIdAndProductId(cart.getId(), orderItem.getProduct().getId())
                            .orElseGet(() -> newCartItem(cart, orderItem.getProduct()));
                    cartItem.setQuantity(cartItem.getQuantity() + orderItem.getQuantity());
                    cartItemRepository.save(cartItem);
                }));
    }

    private CartItem newCartItem(Cart cart, Product product) {
        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(0);
        return cartItem;
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }

        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
