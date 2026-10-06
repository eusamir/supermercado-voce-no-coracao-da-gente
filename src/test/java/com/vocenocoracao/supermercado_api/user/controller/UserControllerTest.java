package com.vocenocoracao.supermercado_api.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.config.SecurityConfig;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.GlobalExceptionHandler;
import com.vocenocoracao.supermercado_api.exceptions.KeycloakIntegrationException;
import com.vocenocoracao.supermercado_api.user.controller.converter.UserRegistrationDTOToUserConverter;
import com.vocenocoracao.supermercado_api.user.controller.converter.UserToUserResponseDTOConverter;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        ModelMapperConfig.class,
        GlobalExceptionHandler.class,
        UserRegistrationDTOToUserConverter.class,
        UserToUserResponseDTOConverter.class
})
class UserControllerTest {

    private static final String VALID_BODY =
            "{\"name\":\"  Marta   Oliveira \",\"email\":\"Marta@Example.com\",\"password\":\"Senha1234\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Marta Oliveira");
        user.setEmail("marta@example.com");
        user.setKeycloakId(UUID.randomUUID());
        return user;
    }

    @Test
    void registrationIsPublicAndReturns201WithoutExposingSecrets() throws Exception {
        when(service.register(any(), eq("Senha1234"))).thenReturn(user());

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Marta Oliveira"))
                .andExpect(jsonPath("$.email").value("marta@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.keycloakId").doesNotExist());
    }

    @Test
    void registrationNormalizesTheNameAndTheEmailBeforeCallingTheService() throws Exception {
        when(service.register(any(), any())).thenReturn(user());

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(service).register(captor.capture(), eq("Senha1234"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getName()).isEqualTo("Marta Oliveira");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmail()).isEqualTo("marta@example.com");
    }

    @Test
    void registrationWithInvalidFieldsIs400WithFieldErrors() throws Exception {
        String body = "{\"name\":\"Marta\",\"email\":\"invalido\",\"password\":\"123\"}";

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Informe nome e sobrenome."))
                .andExpect(jsonPath("$.errors.email").value("O e-mail é inválido."))
                .andExpect(jsonPath("$.errors.password").value("A senha deve ter entre 8 e 128 caracteres."));
        verify(service, never()).register(any(), any());
    }

    @Test
    void registrationWithSpacesAroundTheEmailIs400() throws Exception {
        String body = "{\"name\":\"Marta Oliveira\",\"email\":\" marta@example.com \",\"password\":\"Senha1234\"}";

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("O e-mail é inválido."));
    }

    @Test
    void registrationWithEmptyBodyFieldsIs400() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registrationWithDuplicatedEmailIs409() throws Exception {
        when(service.register(any(), any())).thenThrow(new AlreadyExistsException("E-mail já cadastrado."));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado."));
    }

    @Test
    void registrationWhenKeycloakIsDownIs502() throws Exception {
        when(service.register(any(), any())).thenThrow(new KeycloakIntegrationException("indisponível"));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void currentUserWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserReturnsTheProfileOfTheAuthenticatedUser() throws Exception {
        when(service.getCurrentUser(any())).thenReturn(user());

        mvc.perform(get("/api/users/me").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("marta@example.com"));
    }
}
