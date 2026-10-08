package com.vocenocoracao.supermercado_api.user.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vocenocoracao.supermercado_api.config.KeycloakProperties;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.KeycloakIntegrationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final long TOKEN_EXPIRATION_MARGIN_SECONDS = 30;
    private static final String UNAVAILABLE = "Não foi possível concluir a operação com o serviço de autenticação.";

    private final RestClient restClient;
    private final KeycloakProperties properties;

    private String cachedToken;
    private Instant cachedTokenExpiration = Instant.EPOCH;

    public KeycloakAdminClient(RestClient.Builder restClientBuilder, KeycloakProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public UUID createUser(String email, String firstName, String lastName, String password) {
        KeycloakUser user = new KeycloakUser(
                email,
                email,
                firstName,
                lastName,
                true,
                true,
                List.of(new KeycloakCredential("password", password, false))
        );

        try {
            URI location = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();

            if (location == null) {
                throw new KeycloakIntegrationException(UNAVAILABLE);
            }

            String path = location.getPath();
            return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new AlreadyExistsException("E-mail já cadastrado.");
            }
            if (exception.getStatusCode().value() == 400) {
                log.warn("Keycloak rejeitou a criação do usuário: {}", exception.getResponseBodyAsString());
                throw new InvalidRequestException("Não foi possível criar a conta com os dados informados.");
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public void assignRealmRole(UUID userId, String roleName) {
        try {
            List<Map<String, Object>> availableRoles = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/available", properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> selectedRoles = availableRoles == null
                    ? List.of()
                    : availableRoles.stream().filter(role -> roleName.equals(role.get("name"))).toList();

            if (selectedRoles.isEmpty()) {
                throw new KeycloakIntegrationException("Papel " + roleName + " não encontrado no serviço de autenticação.");
            }

            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(selectedRoles)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public void deleteUser(UUID userId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiration)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response;

        try {
            response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }

        if (response == null || response.accessToken() == null) {
            throw new KeycloakIntegrationException(UNAVAILABLE);
        }

        cachedToken = response.accessToken();
        cachedTokenExpiration = Instant.now().plusSeconds(response.expiresIn() - TOKEN_EXPIRATION_MARGIN_SECONDS);
        return cachedToken;
    }

    private KeycloakIntegrationException unavailable(Exception cause) {
        log.error("Falha na comunicação com o Keycloak", cause);
        return new KeycloakIntegrationException(UNAVAILABLE, cause);
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }

    private record KeycloakUser(
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            boolean emailVerified,
            List<KeycloakCredential> credentials
    ) {
    }

    private record KeycloakCredential(String type, String value, boolean temporary) {
    }
}
