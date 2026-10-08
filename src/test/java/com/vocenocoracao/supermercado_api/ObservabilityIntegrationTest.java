package com.vocenocoracao.supermercado_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String prometheus() throws Exception {
        return mockMvc.perform(get("/actuator/prometheus").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private double counterValue(String scrape, String name, String label) {
        Matcher matcher = Pattern.compile(
                "^" + Pattern.quote(name) + "\\{[^}]*" + label + "[^}]*} ([0-9.eE+-]+)$", Pattern.MULTILINE).matcher(scrape);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    @Test
    void livenessAndReadinessArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anonymousUsersOnlySeeTheStatusWhileAdministratorsSeeEachComponent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health").with(admin()))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    void readinessDependsOnTheDatabaseAndTheBrokerButNotOnTheCache() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness").with(admin()))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"))
                .andExpect(jsonPath("$.components.redis").doesNotExist());
    }

    @Test
    void metricsEndpointsRequireTheAdministratorRole() throws Exception {
        for (String path : new String[]{"/actuator/prometheus", "/actuator/metrics"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).with(customer())).andExpect(status().isForbidden());
            mockMvc.perform(get(path).with(admin())).andExpect(status().isOk());
        }
    }

    @Test
    void prometheusExposesTheApplicationTagAndTheJvmMetrics() throws Exception {
        String scrape = prometheus();

        assertThat(scrape).contains("application=\"supermercado-api\"").contains("jvm_memory_used_bytes");
    }

    @Test
    void everyResponseCarriesARequestIdAndTheCallersIdIsKept() throws Exception {
        String generated = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(generated)).isNotNull();

        mockMvc.perform(get("/api/products").header("X-Request-Id", "frontend-42"))
                .andExpect(status().isOk())
                .andExpect(header()
                        .string("X-Request-Id", "frontend-42"));

        String onError = mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(onError).isNotBlank();
    }

    @Test
    void aRealPurchaseShowsUpInTheBusinessCounters() throws Exception {
        String before = prometheus();
        double created = counterValue(before, "supermercado_orders_placed_total", "");
        double approved = counterValue(before, "supermercado_payments_processed_total", "result=\"approved\"");
        double paid = counterValue(before, "supermercado_orders_fulfilled_total", "result=\"paid\"");

        Category category = new Category();
        category.setName("Categoria métricas " + UUID.randomUUID());
        category.setActive(true);
        Category savedCategory = categoryService.create(category);
        Product product = new Product();
        product.setName("Produto métricas " + UUID.randomUUID());
        product.setDescription("Produto para o teste de métricas");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setActive(true);
        Product savedProduct = productService.create(product, savedCategory.getId());

        User user = new User();
        user.setKeycloakId(UUID.randomUUID());
        user.setName("Marta Oliveira");
        user.setEmail(UUID.randomUUID() + "@example.com");
        User buyer = userRepository.saveAndFlush(user);

        cartService.addItem(buyer, savedProduct.getId(), 2);
        UUID orderId = orderService.checkout(buyer).order().getId();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String after = prometheus();
            assertThat(counterValue(after, "supermercado_orders_placed_total", "")).isEqualTo(created + 1);
            assertThat(counterValue(after, "supermercado_payments_processed_total", "result=\"approved\"")).isEqualTo(approved + 1);
            assertThat(counterValue(after, "supermercado_orders_fulfilled_total", "result=\"paid\"")).isEqualTo(paid + 1);
        });
    }
}
