package com.vocenocoracao.supermercado_api.order.controller;

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

    @PostMapping("/api/orders/checkout")
    public ResponseEntity<OrderResponseDTO> checkout(@AuthenticationPrincipal Jwt jwt) {
        OrderResponseDTO response = modelMapper.map(
                orderService.checkout(userService.getCurrentUser(jwt)),
                OrderResponseDTO.class
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/orders")
    public Page<OrderSummaryResponseDTO> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject Pageable pageable
    ) {
        return orderService.findAll(userService.getCurrentUser(jwt), pageable)
                .map(orderSummary -> modelMapper.map(orderSummary, OrderSummaryResponseDTO.class));
    }

    @GetMapping("/api/orders/{id}")
    public OrderResponseDTO findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return modelMapper.map(orderService.findById(userService.getCurrentUser(jwt), id), OrderResponseDTO.class);
    }
}
