package com.vocenocoracao.supermercado_api.user.controller;

import com.vocenocoracao.supermercado_api.user.dto.UserRegistrationDTO;
import com.vocenocoracao.supermercado_api.user.dto.UserResponseDTO;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final UserService userService;
    private final ModelMapper modelMapper;

    public UserController(UserService userService, ModelMapper modelMapper) {
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @PostMapping("/api/users")
    public ResponseEntity<UserResponseDTO> register(@Validated @RequestBody UserRegistrationDTO request) {
        User user = userService.register(modelMapper.map(request, User.class), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(modelMapper.map(user, UserResponseDTO.class));
    }

    @GetMapping("/api/users/me")
    public UserResponseDTO findCurrent(@AuthenticationPrincipal Jwt jwt) {
        return modelMapper.map(userService.getCurrentUser(jwt), UserResponseDTO.class);
    }
}
