package com.vocenocoracao.supermercado_api.cart.controller;

import com.vocenocoracao.supermercado_api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Carrinho", description = "Carrinho do usuário logado.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
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

    @Operation(summary = "Consultar carrinho", description = "Itens, subtotais, quantidade total e total. Cada item traz available, que indica se o produto segue ativo e com estoque. Não cria carrinho se ele não existir.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido.")
    })
    @GetMapping("/api/cart")
    public CartResponseDTO findCart(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(cartService.getCart(userService.getCurrentUser(jwt)));
    }

    @Operation(summary = "Adicionar item", description = "Cria o carrinho no primeiro uso e soma a quantidade se o produto já estiver nele. Valida produto ativo e estoque, de 1 a 999 unidades.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado ou indisponível."),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente.")
    })
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

    @Operation(summary = "Alterar quantidade", description = "Define a nova quantidade do item. A quantidade 0 remove o item.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho."),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente.")
    })
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

    @Operation(summary = "Remover item", description = "Remove o produto do carrinho e devolve o carrinho atualizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho.")
    })
    @DeleteMapping("/api/cart/items/{productId}")
    public CartResponseDTO removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return toResponse(cartService.removeItem(userService.getCurrentUser(jwt), productId));
    }

    private CartResponseDTO toResponse(CartDetails details) {
        return modelMapper.map(details, CartResponseDTO.class);
    }
}
