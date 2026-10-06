package com.vocenocoracao.supermercado_api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegistrationDTO(
        @Schema(description = "Full name (first and last name)", example = "Marta Oliveira")
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.")
        @Pattern(regexp = "^\\s*\\S+(\\s+\\S+)+\\s*$", message = "Informe nome e sobrenome.")
        String name,

        @Schema(description = "E-mail, also used to log in", example = "marta@example.com")
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "O e-mail é inválido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,

        @Schema(description = "Password with at least 8 characters", example = "Senha1234")
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, max = 128, message = "A senha deve ter entre 8 e 128 caracteres.")
        String password
) {
}
