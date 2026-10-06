package com.vocenocoracao.supermercado_api.order.service.impl;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderDetails;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String EMPTY_CART = "O carrinho está vazio.";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public OrderServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public OrderDetails checkout(User user) {
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new InvalidRequestException(EMPTY_CART));

        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new InvalidRequestException(EMPTY_CART);
        }

        ensureAvailability(cartItems);

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setTotal(total(cartItems));
        order = orderRepository.save(order);

        List<OrderItem> orderItems = orderItemRepository.saveAll(toOrderItems(order, cartItems));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotal());
        payment = paymentRepository.save(payment);

        cartItemRepository.deleteAll(cartItems);
        cartItemRepository.flush();

        return new OrderDetails(order, orderItems, payment);
    }

    private void ensureAvailability(List<CartItem> cartItems) {
        String unavailable = cartItems.stream()
                .map(CartItem::getProduct)
                .filter(product -> !product.isActive() || !product.getCategory().isActive())
                .map(Product::getName)
                .collect(Collectors.joining(", "));

        if (!unavailable.isEmpty()) {
            throw new InvalidRequestException("Produtos indisponíveis: " + unavailable + ".");
        }

        String insufficient = cartItems.stream()
                .filter(item -> item.getQuantity() > item.getProduct().getStock())
                .map(item -> item.getProduct().getName() + " (disponível: " + item.getProduct().getStock() + ")")
                .collect(Collectors.joining(", "));

        if (!insufficient.isEmpty()) {
            throw new InsufficientStockException("Estoque insuficiente para: " + insufficient + ".");
        }
    }

    private BigDecimal total(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<OrderItem> toOrderItems(Order order, List<CartItem> cartItems) {
        return cartItems.stream().map(cartItem -> {
            Product product = cartItem.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setName(product.getName());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            return orderItem;
        }).toList();
    }
}
