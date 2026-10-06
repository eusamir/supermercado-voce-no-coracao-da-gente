package com.vocenocoracao.supermercado_api.cart.controller;

import com.vocenocoracao.supermercado_api.cart.dto.CartItemAddDTO;
import com.vocenocoracao.supermercado_api.cart.dto.CartItemUpdateDTO;
import com.vocenocoracao.supermercado_api.cart.dto.CartResponseDTO;
import com.vocenocoracao.supermercado_api.cart.service.CartDetails;
import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {
    private final CartService cartService;
    private final UserService userService;
    private final ModelMapper modelMapper;

    public CartController(CartService cartService, UserService userService, ModelMapper modelMapper) {
        this.cartService = cartService;
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/api/cart")
    public CartResponseDTO findCart(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(cartService.getCart(userService.getCurrentUser(jwt)));
    }

    @PostMapping("/api/cart/items")
    public CartResponseDTO addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Validated @RequestBody CartItemAddDTO request
    ) {
        return toResponse(cartService.addItem(
                userService.getCurrentUser(jwt),
                request.productId(),
                request.quantity()
        ));
    }

    @PutMapping("/api/cart/items/{productId}")
    public CartResponseDTO updateItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Validated @RequestBody CartItemUpdateDTO request
    ) {
        return toResponse(cartService.updateItem(
                userService.getCurrentUser(jwt),
                productId,
                request.quantity()
        ));
    }

    @DeleteMapping("/api/cart/items/{productId}")
    public CartResponseDTO removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return toResponse(cartService.removeItem(userService.getCurrentUser(jwt), productId));
    }

    private CartResponseDTO toResponse(CartDetails details) {
        return modelMapper.map(details, CartResponseDTO.class);
    }
}
