package com.vocenocoracao.supermercado_api.order.controller;

import com.vocenocoracao.supermercado_api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.vocenocoracao.supermercado_api.order.dto.OrderResponseDTO;
import com.vocenocoracao.supermercado_api.order.dto.OrderSummaryResponseDTO;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pedidos", description = "Checkout, pagamento e consulta de pedidos.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class OrderController {
    private final OrderService orderService;
    private final UserService userService;
    private final ModelMapper modelMapper;

    public OrderController(OrderService orderService, UserService userService, ModelMapper modelMapper) {
        this.orderService = orderService;
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @Operation(summary = "Finalizar compra", description = "Transforma o carrinho em pedido PAYMENT_PENDING, guarda o preço da hora da compra e esvazia o carrinho. O pagamento é processado de forma assíncrona: consulte o pedido para ver PAID, PAYMENT_DECLINED ou CANCELLED. O estoque só baixa depois da aprovação.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado, com pagamento pendente."),
            @ApiResponse(responseCode = "400", description = "Carrinho vazio ou com produtos indisponíveis."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente.")
    })
    @PostMapping("/api/orders/checkout")
    public ResponseEntity<OrderResponseDTO> checkout(@AuthenticationPrincipal Jwt jwt) {
        OrderResponseDTO response = modelMapper.map(
                orderService.checkout(userService.getCurrentUser(jwt)),
                OrderResponseDTO.class
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar meus pedidos", description = "Só os pedidos do usuário logado, do mais recente para o mais antigo, paginados. Não aceita ordenação.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Ordenação não suportada."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido.")
    })
    @GetMapping("/api/orders")
    public Page<OrderSummaryResponseDTO> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject Pageable pageable
    ) {
        return orderService.findAll(userService.getCurrentUser(jwt), pageable)
                .map(orderSummary -> modelMapper.map(orderSummary, OrderSummaryResponseDTO.class));
    }

    @Operation(summary = "Detalhar meu pedido", description = "Itens com o preço da hora da compra e o pagamento, com o motivo de recusa ou estorno quando houver. Pedido de outro usuário também devolve 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.")
    })
    @GetMapping("/api/orders/{id}")
    public OrderResponseDTO findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return modelMapper.map(orderService.findById(userService.getCurrentUser(jwt), id), OrderResponseDTO.class);
    }
}
