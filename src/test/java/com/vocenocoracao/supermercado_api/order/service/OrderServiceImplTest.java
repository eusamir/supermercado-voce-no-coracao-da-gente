package com.vocenocoracao.supermercado_api.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.order.entity.Order;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.impl.OrderServiceImpl;
import com.vocenocoracao.supermercado_api.orderItem.entity.OrderItem;
import com.vocenocoracao.supermercado_api.orderItem.repository.OrderItemRepository;
import com.vocenocoracao.supermercado_api.payment.entity.Payment;
import com.vocenocoracao.supermercado_api.payment.entity.PaymentStatus;
import com.vocenocoracao.supermercado_api.payment.repository.PaymentRepository;
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

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

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

    @InjectMocks
    private OrderServiceImpl service;

    private User user;
    private Cart cart;
    private Category category;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());

        cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUser(user);

        category = new Category();
        category.setId(UUID.randomUUID());
        category.setActive(true);
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setActive(true);
        product.setCategory(category);
        return product;
    }

    private CartItem item(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private void stubPersistence() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checkoutCreatesTheOrderItemsAndAPendingPayment() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        Product arroz = product("Arroz branco 5kg", "28.90", 5);
        List<CartItem> cartItems = List.of(item(banana, 3), item(arroz, 1));
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(cartItems);
        stubPersistence();

        OrderDetails details = service.checkout(user);

        assertThat(details.order().getUser()).isSameAs(user);
        assertThat(details.order().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(details.order().getTotal()).isEqualByComparingTo("49.87");
        assertThat(details.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(details.payment().getAmount()).isEqualByComparingTo("49.87");
        assertThat(details.payment().getOrder()).isSameAs(details.order());
        assertThat(details.payment().getTransactionId()).isNull();
    }

    @Test
    void checkoutSnapshotsTheNameAndThePriceOfEachItem() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item(banana, 3)));
        stubPersistence();

        OrderDetails details = service.checkout(user);

        OrderItem orderItem = details.items().getFirst();
        assertThat(orderItem.getOrder()).isSameAs(details.order());
        assertThat(orderItem.getProduct()).isSameAs(banana);
        assertThat(orderItem.getName()).isEqualTo("Banana prata (kg)");
        assertThat(orderItem.getUnitPrice()).isEqualByComparingTo("6.99");
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getSubtotal()).isEqualByComparingTo("20.97");
    }

    @Test
    void checkoutEmptiesTheCartAndDoesNotTouchTheStock() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        List<CartItem> cartItems = List.of(item(banana, 3));
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(cartItems);
        stubPersistence();

        service.checkout(user);

        verify(cartItemRepository).deleteAll(cartItems);
        assertThat(banana.getStock()).isEqualTo(10);
    }

    @Test
    void checkoutAcceptsExactlyTheAvailableStock() {
        Product banana = product("Banana prata (kg)", "6.99", 3);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item(banana, 3)));
        stubPersistence();

        assertThat(service.checkout(user).order().getTotal()).isEqualByComparingTo("20.97");
    }

    @Test
    void checkoutFailsWhenTheUserHasNoCart() {
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("O carrinho está vazio.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutFailsWhenTheCartHasNoItems() {
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.checkout(user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("O carrinho está vazio.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutReportsAllProductsWithInsufficientStockAndCreatesNothing() {
        Product banana = product("Banana prata (kg)", "6.99", 2);
        Product arroz = product("Arroz branco 5kg", "28.90", 0);
        Product leite = product("Leite integral 1L", "5.29", 50);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId()))
                .thenReturn(List.of(item(banana, 3), item(arroz, 1), item(leite, 1)));

        assertThatThrownBy(() -> service.checkout(user))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Estoque insuficiente para: Banana prata (kg) (disponível: 2), Arroz branco 5kg (disponível: 0).");
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteAll(any());
    }

    @Test
    void checkoutRejectsAnInactiveProduct() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        banana.setActive(false);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item(banana, 1)));

        assertThatThrownBy(() -> service.checkout(user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Produtos indisponíveis: Banana prata (kg).");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutRejectsAProductOfAnInactiveCategory() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        category.setActive(false);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item(banana, 1)));

        assertThatThrownBy(() -> service.checkout(user)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void checkoutPersistsTheOrderBeforeItsItemsAndPayment() {
        Product banana = product("Banana prata (kg)", "6.99", 10);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item(banana, 1)));
        stubPersistence();

        service.checkout(user);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotal()).isEqualByComparingTo("6.99");
    }
}
