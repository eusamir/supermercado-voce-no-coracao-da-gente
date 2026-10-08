package com.vocenocoracao.supermercado_api.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vocenocoracao.supermercado_api.config.KeycloakProperties;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.KeycloakIntegrationException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientTest {

    private static final String BASE_URL = "http://keycloak.test";
    private static final String TOKEN_URL = BASE_URL + "/realms/supermercado/protocol/openid-connect/token";
    private static final String USERS_URL = BASE_URL + "/admin/realms/supermercado/users";
    private static final String TOKEN_BODY = "{\"access_token\":\"admin-token\",\"expires_in\":300}";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(
                builder,
                new KeycloakProperties(BASE_URL, "supermercado", "supermercado-backend", "secret")
        );
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=supermercado-backend")))
                .andExpect(content().string(containsString("client_secret=secret")))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
    }

    @Test
    void createUserSendsTheRepresentationAndReturnsTheIdFromTheLocationHeader() {
        UUID userId = UUID.randomUUID();
        expectToken();
        server.expect(requestTo(USERS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(jsonPath("$.username").value("marta@example.com"))
                .andExpect(jsonPath("$.email").value("marta@example.com"))
                .andExpect(jsonPath("$.firstName").value("Marta"))
                .andExpect(jsonPath("$.lastName").value("Oliveira Santos"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.credentials[0].type").value("password"))
                .andExpect(jsonPath("$.credentials[0].value").value("Senha1234"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create(USERS_URL + "/" + userId)));

        UUID created = client.createUser("marta@example.com", "Marta", "Oliveira Santos", "Senha1234");

        assertThat(created).isEqualTo(userId);
        server.verify();
    }

    @Test
    void accessTokenIsCachedAcrossCalls() {
        UUID userId = UUID.randomUUID();
        expectToken();
        server.expect(requestTo(USERS_URL + "/" + userId)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        server.expect(requestTo(USERS_URL + "/" + userId)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        client.deleteUser(userId);
        client.deleteUser(userId);

        server.verify();
    }

    @Test
    void createUserMapsConflictToAlreadyExists() {
        expectToken();
        server.expect(requestTo(USERS_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"User exists with same email\"}"));

        assertThatThrownBy(() -> client.createUser("marta@example.com", "Marta", "Silva", "Senha1234"))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("E-mail já cadastrado.");
    }

    @Test
    void createUserMapsBadRequestToInvalidRequest() {
        expectToken();
        server.expect(requestTo(USERS_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"Invalid password\"}"));

        assertThatThrownBy(() -> client.createUser("marta@example.com", "Marta", "Silva", "Senha1234"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createUserMapsServerErrorToIntegrationException() {
        expectToken();
        server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.createUser("marta@example.com", "Marta", "Silva", "Senha1234"))
                .isInstanceOf(KeycloakIntegrationException.class);
    }

    @Test
    void createUserFailsWhenTheLocationHeaderIsMissing() {
        expectToken();
        server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CREATED));

        assertThatThrownBy(() -> client.createUser("marta@example.com", "Marta", "Silva", "Senha1234"))
                .isInstanceOf(KeycloakIntegrationException.class);
    }

    @Test
    void tokenFailureIsReportedAsIntegrationException() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createUser("marta@example.com", "Marta", "Silva", "Senha1234"))
                .isInstanceOf(KeycloakIntegrationException.class);
    }

    @Test
    void assignRealmRoleMapsTheRoleFoundAmongTheAvailableOnes() {
        UUID userId = UUID.randomUUID();
        expectToken();
        server.expect(requestTo(USERS_URL + "/" + userId + "/role-mappings/realm/available"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"role-1\",\"name\":\"offline_access\"},{\"id\":\"role-2\",\"name\":\"CUSTOMER\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS_URL + "/" + userId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("role-2"))
                .andExpect(jsonPath("$[0].name").value("CUSTOMER"))
                .andRespond(withNoContent());

        client.assignRealmRole(userId, "CUSTOMER");

        server.verify();
    }

    @Test
    void assignRealmRoleFailsWhenTheRoleDoesNotExist() {
        UUID userId = UUID.randomUUID();
        expectToken();
        server.expect(requestTo(USERS_URL + "/" + userId + "/role-mappings/realm/available"))
                .andRespond(withSuccess("[{\"id\":\"role-1\",\"name\":\"offline_access\"}]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.assignRealmRole(userId, "CUSTOMER"))
                .isInstanceOf(KeycloakIntegrationException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void deleteUserFailureIsReportedAsIntegrationException() {
        UUID userId = UUID.randomUUID();
        expectToken();
        server.expect(requestTo(USERS_URL + "/" + userId)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.deleteUser(userId)).isInstanceOf(KeycloakIntegrationException.class);
    }
}
