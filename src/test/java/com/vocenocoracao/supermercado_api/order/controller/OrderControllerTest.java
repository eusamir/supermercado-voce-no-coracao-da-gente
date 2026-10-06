package com.vocenocoracao.supermercado_api.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.config.SecurityConfig;
import com.vocenocoracao.supermercado_api.exceptions.GlobalExceptionHandler;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.order.controller.converter.OrderDetailsToOrderResponseDTOConverter;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.service.OrderDetails;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OrderController.class)
@Import({
        SecurityConfig.class,
        ModelMapperConfig.class,
        GlobalExceptionHandler.class,
        OrderDetailsToOrderResponseDTOConverter.class
})
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        when(userService.getCurrentUser(any())).thenReturn(user);
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()));
    }

    private OrderDetails details() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setTotal(new BigDecimal("49.87"));
        order.setCreatedAt(Instant.parse("2026-10-06T12:00:00Z"));

        Product banana = new Product();
        banana.setId(UUID.randomUUID());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(banana);
        item.setName("Banana prata (kg)");
        item.setUnitPrice(new BigDecimal("6.99"));
        item.setQuantity(3);
        item.setSubtotal(new BigDecimal("20.97"));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("49.87"));

        return new OrderDetails(order, List.of(item), payment);
    }

    @Test
    void checkoutWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/orders/checkout")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutReturns201WithTheOrderItemsAndThePendingPayment() throws Exception {
        when(orderService.checkout(user)).thenReturn(details());

        mvc.perform(post("/api/orders/checkout").with(customer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.total").value(49.87))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.items[0].name").value("Banana prata (kg)"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(6.99))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].subtotal").value(20.97))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.amount").value(49.87));
    }

    @Test
    void checkoutOfAnEmptyCartIs400() throws Exception {
        when(orderService.checkout(user)).thenThrow(new InvalidRequestException("O carrinho está vazio."));

        mvc.perform(post("/api/orders/checkout").with(customer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O carrinho está vazio."));
    }

    @Test
    void checkoutWithoutStockIs409() throws Exception {
        when(orderService.checkout(user))
                .thenThrow(new InsufficientStockException("Estoque insuficiente para: Banana prata (kg) (disponível: 2)."));

        mvc.perform(post("/api/orders/checkout").with(customer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Estoque insuficiente"));
    }
}
