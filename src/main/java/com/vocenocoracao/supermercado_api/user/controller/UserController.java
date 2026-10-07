package com.vocenocoracao.supermercado_api.user.controller;

import com.vocenocoracao.supermercado_api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Usuários", description = "Cadastro e perfil do usuário logado.")
@RestController
public class UserController {
    private final UserService userService;
    private final ModelMapper modelMapper;

    public UserController(UserService userService, ModelMapper modelMapper) {
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @Operation(summary = "Cadastrar usuário", description = "Público. Cria a conta no Keycloak com o papel CUSTOMER e o perfil local. Nome e sobrenome são obrigatórios e a senha deve ter de 8 a 128 caracteres.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos. Os erros por campo vêm em errors."),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado."),
            @ApiResponse(responseCode = "502", description = "Serviço de autenticação indisponível.")
    })
    @PostMapping("/api/users")
    public ResponseEntity<UserResponseDTO> register(@Validated @RequestBody UserRegistrationDTO request) {
        User user = userService.register(modelMapper.map(request, User.class), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(modelMapper.map(user, UserResponseDTO.class));
    }

    @Operation(summary = "Consultar meu perfil", description = "Devolve o perfil do usuário do token. No primeiro acesso, o perfil local é criado a partir do token.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/api/users/me")
    public UserResponseDTO findCurrent(@AuthenticationPrincipal Jwt jwt) {
        return modelMapper.map(userService.getCurrentUser(jwt), UserResponseDTO.class);
    }
}
