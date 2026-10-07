package com.vocenocoracao.supermercado_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JourneyIntegrationTest {

    private static final String REALM = "supermercado";
    private static final String BACKEND_SECRET = "journey-backend-secret";
    private static final String PASSWORD = "Senha1234";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.7.5"))
            .withExposedPorts(7080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KEYCLOAK_BACKEND_CLIENT_SECRET", BACKEND_SECRET)
            .withCommand("start-dev", "--http-port=7080", "--import-realm")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("keycloak/realm/supermercado-realm.json"),
                    "/opt/keycloak/data/import/supermercado-realm.json")
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(7080).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("keycloak.base-url", JourneyIntegrationTest::keycloakBaseUrl);
        registry.add("keycloak.client-secret", () -> BACKEND_SECRET);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloakBaseUrl() + "/realms/" + REALM);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> keycloakBaseUrl() + "/realms/" + REALM + "/protocol/openid-connect/certs");
    }

    private static String keycloakBaseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(7080);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private String login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "supermercado-frontend");
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);

        JsonNode token = RestClient.create().post()
                .uri(keycloakBaseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        return token.get("access_token").asText();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String uniqueEmail() {
        return "jornada." + UUID.randomUUID() + "@example.com";
    }

    private String registerAndLogin() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> registration = call(HttpMethod.POST, "/api/users", null,
                Map.of("name", "Marta Oliveira", "email", email, "password", PASSWORD));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email, PASSWORD);
    }

    private JsonNode firstProduct(String search) {
        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/products?size=1&search=" + search, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("content").get(0);
    }

    private int stockOf(String productId) {
        return call(HttpMethod.GET, "/api/products/" + productId, null, null).getBody().get("stock").asInt();
    }

    private void addToCart(String token, String productId, int quantity) {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/cart/items", token,
                Map.of("productId", productId, "quantity", quantity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String checkout(String token) {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/orders/checkout", token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    private JsonNode awaitOrderStatus(String token, String orderId, String expectedStatus) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode order = call(HttpMethod.GET, "/api/orders/" + orderId, token, null).getBody();
            assertThat(order.get("status").asText()).isEqualTo(expectedStatus);
        });
        return call(HttpMethod.GET, "/api/orders/" + orderId, token, null).getBody();
    }

    @Test
    void aCustomerRegistersBuysAndSeesTheOrderPaid() {
        assertThat(call(HttpMethod.GET, "/api/cart", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String email = uniqueEmail();
        ResponseEntity<JsonNode> registration = call(HttpMethod.POST, "/api/users", null,
                Map.of("name", "Marta Oliveira", "email", email, "password", PASSWORD));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registration.getBody().get("email").asText()).isEqualTo(email);
        assertThat(registration.getBody().has("password")).isFalse();

        String token = login(email, PASSWORD);
        ResponseEntity<JsonNode> profile = call(HttpMethod.GET, "/api/users/me", token, null);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("email").asText()).isEqualTo(email);

        JsonNode banana = firstProduct("banana");
        JsonNode arroz = firstProduct("arroz");
        int bananaStockBefore = banana.get("stock").asInt();

        addToCart(token, banana.get("id").asText(), 3);
        addToCart(token, arroz.get("id").asText(), 1);

        JsonNode cart = call(HttpMethod.GET, "/api/cart", token, null).getBody();
        assertThat(cart.get("items")).hasSize(2);
        assertThat(cart.get("itemCount").asInt()).isEqualTo(4);
        assertThat(cart.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("49.87"));

        ResponseEntity<JsonNode> checkout = call(HttpMethod.POST, "/api/orders/checkout", token, null);
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkout.getBody().get("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(checkout.getBody().at("/payment/status").asText()).isEqualTo("PENDING");
        String orderId = checkout.getBody().get("id").asText();

        assertThat(call(HttpMethod.GET, "/api/cart", token, null).getBody().get("items")).isEmpty();

        JsonNode paid = awaitOrderStatus(token, orderId, "PAID");
        assertThat(paid.at("/payment/status").asText()).isEqualTo("APPROVED");
        assertThat(paid.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("49.87"));
        assertThat(paid.get("items")).hasSize(2);

        JsonNode orders = call(HttpMethod.GET, "/api/orders", token, null).getBody();
        assertThat(orders.at("/page/totalElements").asInt()).isEqualTo(1);
        assertThat(orders.at("/content/0/status").asText()).isEqualTo("PAID");
        assertThat(orders.at("/content/0/paymentStatus").asText()).isEqualTo("APPROVED");

        assertThat(stockOf(banana.get("id").asText())).isEqualTo(bananaStockBefore - 3);
    }

    @Test
    void anAmountAboveTheGatewayLimitIsDeclinedWithTheReasonAndTheStockIsKept() {
        String token = registerAndLogin();
        JsonNode arroz = firstProduct("arroz");
        int stockBefore = arroz.get("stock").asInt();

        addToCart(token, arroz.get("id").asText(), 40);
        String orderId = checkout(token);

        JsonNode declined = awaitOrderStatus(token, orderId, "PAYMENT_DECLINED");

        assertThat(declined.at("/payment/status").asText()).isEqualTo("DECLINED");
        assertThat(declined.at("/payment/failureReason").asText()).contains("limite");
        assertThat(stockOf(arroz.get("id").asText())).isEqualTo(stockBefore);
    }

    @Test
    void customersAreBlockedFromAdministrationWhileTheAdministratorManagesTheCatalog() {
        String customerToken = login("cliente", "123456");
        String adminToken = login("admin", "123456");

        assertThat(call(HttpMethod.GET, "/api/admin/products", customerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/categories", customerToken, Map.of("name", "Nova")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/admin/products", adminToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode categories = call(HttpMethod.GET, "/api/categories?size=1&search=Mercearia", adminToken, null).getBody();
        String categoryId = categories.at("/content/0/id").asText();

        String productName = "Produto da jornada " + UUID.randomUUID();
        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/api/products", adminToken, Map.of(
                "name", productName,
                "description", "Criado pelo teste de jornada",
                "price", new BigDecimal("12.50"),
                "stock", 5,
                "categoryId", categoryId));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String productId = created.getBody().get("id").asText();

        assertThat(call(HttpMethod.GET, "/api/products/" + productId, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        call(HttpMethod.PATCH, "/api/products/" + productId + "/active", adminToken, Map.of("active", false));
        assertThat(call(HttpMethod.GET, "/api/products/" + productId, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/admin/products/" + productId, adminToken, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        call(HttpMethod.PATCH, "/api/products/" + productId + "/active", adminToken, Map.of("active", true));
        assertThat(call(HttpMethod.GET, "/api/products/" + productId, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String buyerToken = registerAndLogin();
        addToCart(buyerToken, productId, 5);
        String orderId = checkout(buyerToken);
        awaitOrderStatus(buyerToken, orderId, "PAID");

        assertThat(stockOf(productId)).isZero();
        ResponseEntity<JsonNode> soldOut = call(HttpMethod.POST, "/api/cart/items", buyerToken,
                Map.of("productId", productId, "quantity", 1));
        assertThat(soldOut.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(soldOut.getBody().get("detail").asText()).contains("Estoque insuficiente");
    }

    @Test
    void ordersAreIsolatedBetweenCustomers() {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        addToCart(owner, firstProduct("banana").get("id").asText(), 1);
        String orderId = checkout(owner);

        ResponseEntity<JsonNode> foreign = call(HttpMethod.GET, "/api/orders/" + orderId, other, null);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/orders", other, null).getBody().at("/page/totalElements").asInt()).isZero();
        assertThat(call(HttpMethod.GET, "/api/orders/" + orderId, owner, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registrationRejectsDuplicatedEmailAndInvalidData() {
        String email = uniqueEmail();
        Map<String, String> body = Map.of("name", "Marta Oliveira", "email", email, "password", PASSWORD);

        assertThat(call(HttpMethod.POST, "/api/users", null, body).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> duplicated = call(HttpMethod.POST, "/api/users", null, body);
        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicated.getBody().get("detail").asText()).isEqualTo("E-mail já cadastrado.");

        ResponseEntity<JsonNode> invalid = call(HttpMethod.POST, "/api/users", null,
                Map.of("name", "Marta", "email", "invalido", "password", "123"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().at("/errors/name").asText()).isEqualTo("Informe nome e sobrenome.");
        assertThat(invalid.getBody().at("/errors/email").asText()).isEqualTo("O e-mail é inválido.");
        assertThat(invalid.getBody().has("errors")).isTrue();
    }
}
