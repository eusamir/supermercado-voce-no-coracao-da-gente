package com.vocenocoracao.supermercado_api.order.controller;

import com.vocenocoracao.supermercado_api.order.dto.OrderResponseDTO;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
}
