package com.vocenocoracao.supermercado_api.cart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.cart.controller.converter.CartDetailsToCartResponseDTOConverter;
import com.vocenocoracao.supermercado_api.cart.entity.Cart;
import com.vocenocoracao.supermercado_api.cart.service.CartDetails;
import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.cartItem.entity.CartItem;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.config.SecurityConfig;
import com.vocenocoracao.supermercado_api.exceptions.GlobalExceptionHandler;
import com.vocenocoracao.supermercado_api.exceptions.InsufficientStockException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CartController.class)
@Import({
        SecurityConfig.class,
        ModelMapperConfig.class,
        GlobalExceptionHandler.class,
        CartDetailsToCartResponseDTOConverter.class
})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User user;
    private Product banana;
    private Product arroz;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        when(userService.getCurrentUser(any())).thenReturn(user);

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setActive(true);

        banana = product("Banana prata (kg)", "6.99", 10, category);
        arroz = product("Arroz branco 5kg", "28.90", 1, category);
    }

    private Product product(String name, String price, int stock, Category category) {
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
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private CartDetails cartWith(CartItem... items) {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        return new CartDetails(cart, List.of(items));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()));
    }

    @Test
    void everyCartEndpointRequiresAuthentication() throws Exception {
        UUID productId = UUID.randomUUID();
        String body = "{\"productId\":\"" + productId + "\",\"quantity\":1}";

        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/cart/items/{id}", productId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/cart/items/{id}", productId)).andExpect(status().isUnauthorized());
    }

    @Test
    void emptyCartHasNoIdAndATotalOfZero() throws Exception {
        when(cartService.getCart(user)).thenReturn(new CartDetails(null, List.of()));

        mockMvc.perform(get("/api/cart").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cartShowsSubtotalsAndTheTotal() throws Exception {
        when(cartService.getCart(user)).thenReturn(cartWith(item(banana, 3), item(arroz, 1)));

        mockMvc.perform(get("/api/cart").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Banana prata (kg)"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(6.99))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].subtotal").value(20.97))
                .andExpect(jsonPath("$.items[1].subtotal").value(28.90))
                .andExpect(jsonPath("$.itemCount").value(4))
                .andExpect(jsonPath("$.total").value(49.87));
    }

    @Test
    void cartFlagsAnItemThatIsNoLongerAvailable() throws Exception {
        when(cartService.getCart(user)).thenReturn(cartWith(item(banana, 3), item(arroz, 2)));

        mockMvc.perform(get("/api/cart").with(customer()))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[1].available").value(false))
                .andExpect(jsonPath("$.items[1].stock").value(1));
    }

    @Test
    void cartFlagsAnItemOfAnInactiveProduct() throws Exception {
        banana.setActive(false);
        when(cartService.getCart(user)).thenReturn(cartWith(item(banana, 1)));

        mockMvc.perform(get("/api/cart").with(customer()))
                .andExpect(jsonPath("$.items[0].available").value(false));
    }

    @Test
    void addItemReturnsTheUpdatedCart() throws Exception {
        when(cartService.addItem(user, banana.getId(), 2)).thenReturn(cartWith(item(banana, 2)));

        mockMvc.perform(post("/api/cart/items").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + banana.getId() + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void addItemWithInvalidFieldsIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").value("O produto é obrigatório."))
                .andExpect(jsonPath("$.errors.quantity").value("A quantidade deve ser de pelo menos 1."));
        verify(cartService, never()).addItem(any(), any(), anyInt());
    }

    @Test
    void addItemAboveTheMaximumQuantityIs400() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + banana.getId() + "\",\"quantity\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("A quantidade deve ser de no máximo 999."));
    }

    @Test
    void addItemWithoutStockIs409() throws Exception {
        when(cartService.addItem(any(), any(), anyInt()))
                .thenThrow(new InsufficientStockException("Estoque insuficiente. Disponível: 1."));

        mockMvc.perform(post("/api/cart/items").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + arroz.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Estoque insuficiente. Disponível: 1."));
    }

    @Test
    void addItemOfAnUnknownProductIs404() throws Exception {
        when(cartService.addItem(any(), any(), anyInt()))
                .thenThrow(new NotFoundException("Produto não encontrado."));

        mockMvc.perform(post("/api/cart/items").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItemAllowsZeroToRemove() throws Exception {
        when(cartService.updateItem(user, banana.getId(), 0)).thenReturn(cartWith());

        mockMvc.perform(put("/api/cart/items/{id}", banana.getId()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void updateItemWithNegativeQuantityIs400() throws Exception {
        mockMvc.perform(put("/api/cart/items/{id}", banana.getId()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("A quantidade não pode ser negativa."));
    }

    @Test
    void updateItemWithInvalidProductIdIs400() throws Exception {
        mockMvc.perform(put("/api/cart/items/{id}", "abc").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItemNotInTheCartIs404() throws Exception {
        when(cartService.updateItem(any(), any(), anyInt()))
                .thenThrow(new NotFoundException("Item não encontrado no carrinho."));

        mockMvc.perform(put("/api/cart/items/{id}", UUID.randomUUID()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItemReturnsTheCartWithoutIt() throws Exception {
        when(cartService.removeItem(user, banana.getId())).thenReturn(cartWith(item(arroz, 1)));

        mockMvc.perform(delete("/api/cart/items/{id}", banana.getId()).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Arroz branco 5kg"));
    }

    @Test
    void removeItemNotInTheCartIs404() throws Exception {
        when(cartService.removeItem(any(), eq(arroz.getId())))
                .thenThrow(new NotFoundException("Item não encontrado no carrinho."));

        mockMvc.perform(delete("/api/cart/items/{id}", arroz.getId()).with(customer()))
                .andExpect(status().isNotFound());
    }
}
