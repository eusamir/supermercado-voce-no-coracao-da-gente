package com.vocenocoracao.supermercado_api.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cart.service.impl.CartServiceImpl;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartServiceImpl service;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());

        cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUser(user);

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setActive(true);

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setStock(10);
        product.setActive(true);
        product.setCategory(category);
    }

    private CartItem item(int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void getCartReturnsAnEmptyViewWithoutCreatingAnything() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        CartDetails details = service.getCart(user);

        assertThat(details.cart()).isNull();
        assertThat(details.items()).isEmpty();
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    void getCartReturnsTheItemsOfTheExistingCart() {
        CartItem item = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(item));

        assertThat(service.getCart(user).items()).containsExactly(item);
    }

    @Test
    void addItemCreatesTheCartOnFirstUse() {
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class))).thenReturn(cart);
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        service.addItem(user, product.getId(), 2);

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).saveAndFlush(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void addItemRecoversWhenAConcurrentRequestCreatedTheCartFirst() {
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty(), Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenThrow(new DataIntegrityViolationException("unique"));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        service.addItem(user, product.getId(), 1);

        verify(cartItemRepository).saveAndFlush(any(CartItem.class));
    }

    @Test
    void addItemCreatesANewItemWithTheRequestedQuantity() {
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        service.addItem(user, product.getId(), 3);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getCart()).isSameAs(cart);
    }

    @Test
    void addItemSumsTheQuantityWhenTheProductIsAlreadyInTheCart() {
        CartItem existing = item(4);
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(existing));

        service.addItem(user, product.getId(), 3);

        assertThat(existing.getQuantity()).isEqualTo(7);
        verify(cartItemRepository).saveAndFlush(existing);
    }

    @Test
    void addItemAcceptsExactlyTheAvailableStock() {
        CartItem existing = item(6);
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(existing));

        service.addItem(user, product.getId(), 4);

        assertThat(existing.getQuantity()).isEqualTo(10);
    }

    @Test
    void addItemRejectsASumAboveTheStock() {
        CartItem existing = item(8);
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addItem(user, product.getId(), 3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Estoque insuficiente. Disponível: 10.");
        assertThat(existing.getQuantity()).isEqualTo(8);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void addItemRejectsANewItemAboveTheStock() {
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(user, product.getId(), 11))
                .isInstanceOf(InsufficientStockException.class);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void addItemRejectsAProductThatIsNotVisible() {
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(user, product.getId(), 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Produto não encontrado.");
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateItemSetsTheNewQuantity() {
        CartItem existing = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of(existing));

        service.updateItem(user, product.getId(), 5);

        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).saveAndFlush(existing);
    }

    @Test
    void updateItemRejectsAQuantityAboveTheStock() {
        CartItem existing = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.updateItem(user, product.getId(), 11))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(existing.getQuantity()).isEqualTo(2);
    }

    @Test
    void updateItemWithZeroRemovesTheItemEvenWhenTheProductIsNoLongerVisible() {
        CartItem existing = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        service.updateItem(user, product.getId(), 0);

        verify(cartItemRepository).delete(existing);
        verify(productRepository, never()).findVisibleById(any());
    }

    @Test
    void updateItemRejectsAnIncreaseWhenTheProductIsNoLongerVisible() {
        CartItem existing = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(productRepository.findVisibleById(product.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(user, product.getId(), 3))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateItemFailsWhenTheUserHasNoCart() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(user, product.getId(), 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Item não encontrado no carrinho.");
    }

    @Test
    void updateItemFailsWhenTheProductIsNotInTheCart() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(user, product.getId(), 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeItemDeletesTheItem() {
        CartItem existing = item(2);
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCartId(cart.getId())).thenReturn(List.of());

        assertThat(service.removeItem(user, product.getId()).items()).isEmpty();
        verify(cartItemRepository).delete(existing);
    }

    @Test
    void removeItemFailsWhenTheProductIsNotInTheCart() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(user, product.getId())).isInstanceOf(NotFoundException.class);
        verify(cartItemRepository, never()).delete(any());
    }
}
