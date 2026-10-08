package com.vocenocoracao.supermercado_api.order.service.impl;

import com.vocenocoracao.supermercado_api.common.BusinessMetrics;
import com.vocenocoracao.supermercado_api.config.CacheConfig;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderFulfillmentService;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFulfillmentServiceImpl implements OrderFulfillmentService {

    private static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final BusinessMetrics businessMetrics;

    public OrderFulfillmentServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository,
            BusinessMetrics businessMetrics
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.businessMetrics = businessMetrics;
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCT_LIST, CacheConfig.PRODUCT_DETAIL}, allEntries = true)
    @Override
    @Transactional
    public void fulfill(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Pedido não encontrado."));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado."));

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING || payment.getStatus() != PaymentStatus.APPROVED) {
            return;
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        Map<UUID, Integer> quantityByProduct = new HashMap<>();
        items.forEach(item -> quantityByProduct.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum));

        List<Product> products = productRepository.findAllByIdForUpdate(quantityByProduct.keySet());

        String insufficient = products.stream()
                .filter(product -> quantityByProduct.get(product.getId()) > product.getStock())
                .map(product -> product.getName() + " (disponível: " + product.getStock() + ")")
                .collect(Collectors.joining(", "));

        if (!insufficient.isEmpty()) {
            cancelAndRefund(order, payment, "Estoque insuficiente para: " + insufficient + ".");
            return;
        }

        products.forEach(product -> product.setStock(product.getStock() - quantityByProduct.get(product.getId())));
        productRepository.saveAll(products);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        businessMetrics.orderFulfilled(true);
    }

    private void cancelAndRefund(Order order, Payment payment, String reason) {
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setFailureReason(reason.length() > FAILURE_REASON_MAX_LENGTH
                ? reason.substring(0, FAILURE_REASON_MAX_LENGTH)
                : reason);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        businessMetrics.orderFulfilled(false);
    }
}
