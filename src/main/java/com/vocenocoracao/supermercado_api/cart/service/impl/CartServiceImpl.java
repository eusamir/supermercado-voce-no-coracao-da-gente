package com.vocenocoracao.supermercado_api.cart.service.impl;

import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.repository.CartRepository;
import com.vocenocoracao.supermercado_api.cart.service.CartDetails;
import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.cartItem.repository.CartItemRepository;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService {

    private static final String PRODUCT_NOT_FOUND = "Produto não encontrado.";
    private static final String ITEM_NOT_FOUND = "Item não encontrado no carrinho.";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartDetails getCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .map(cart -> new CartDetails(cart, cartItemRepository.findAllByCartId(cart.getId())))
                .orElseGet(() -> new CartDetails(null, List.of()));
    }

    @Override
    @Transactional
    public CartDetails addItem(User user, UUID productId, int quantity) {
        Product product = findVisibleProduct(productId);
        Cart cart = findOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseGet(() -> newItem(cart, product));

        int requestedQuantity = item.getQuantity() + quantity;
        ensureStock(product, requestedQuantity);

        item.setQuantity(requestedQuantity);
        cartItemRepository.saveAndFlush(item);

        return details(cart);
    }

    @Override
    @Transactional
    public CartDetails updateItem(User user, UUID productId, int quantity) {
        Cart cart = findCartOrThrow(user);
        CartItem item = findItemOrThrow(cart, productId);

        if (quantity == 0) {
            cartItemRepository.delete(item);
            cartItemRepository.flush();
            return details(cart);
        }

        Product product = findVisibleProduct(productId);
        ensureStock(product, quantity);

        item.setQuantity(quantity);
        cartItemRepository.saveAndFlush(item);

        return details(cart);
    }

    @Override
    @Transactional
    public CartDetails removeItem(User user, UUID productId) {
        Cart cart = findCartOrThrow(user);
        CartItem item = findItemOrThrow(cart, productId);

        cartItemRepository.delete(item);
        cartItemRepository.flush();

        return details(cart);
    }

    private Product findVisibleProduct(UUID productId) {
        return productRepository.findVisibleById(productId)
                .orElseThrow(() -> new NotFoundException(PRODUCT_NOT_FOUND));
    }

    private Cart findCartOrThrow(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException(ITEM_NOT_FOUND));
    }

    private CartItem findItemOrThrow(Cart cart, UUID productId) {
        return cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new NotFoundException(ITEM_NOT_FOUND));
    }

    private Cart findOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);

        try {
            return cartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException exception) {
            return cartRepository.findByUserId(user.getId()).orElseThrow(() -> exception);
        }
    }

    private CartItem newItem(Cart cart, Product product) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(0);
        return item;
    }

    private void ensureStock(Product product, int requestedQuantity) {
        if (requestedQuantity > product.getStock()) {
            throw new InsufficientStockException("Estoque insuficiente. Disponível: " + product.getStock() + ".");
        }
    }

    private CartDetails details(Cart cart) {
        return new CartDetails(cart, cartItemRepository.findAllByCartId(cart.getId()));
    }
}
