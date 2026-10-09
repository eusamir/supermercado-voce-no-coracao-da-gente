package com.vocenocoracao.supermercado_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.vocenocoracao.supermercado_api.cart.service.CartService;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.order.entity.OrderStatus;
import com.vocenocoracao.supermercado_api.order.repository.OrderRepository;
import com.vocenocoracao.supermercado_api.order.service.OrderService;
import com.vocenocoracao.supermercado_api.product.controller.ProductCatalogReader;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductPageDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.repository.ProductRepository;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cache.products.ttl=5s")
class ProductCatalogCacheIntegrationTest {

    private static final String LIST_KEY_PATTERN = "supermercado:productList::*";
    private static final String DETAIL_KEY_PATTERN = "supermercado:productDetail::*";
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private ProductCatalogReader catalogReader;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void emptyTheCache() {
        Set<String> keys = redisTemplate.keys("supermercado:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Category newCategory() {
        Category category = new Category();
        category.setName("Categoria cache " + UUID.randomUUID());
        category.setActive(true);
        return categoryService.create(category);
    }

    private Product newProduct(Category category, int stock) {
        Product product = new Product();
        product.setName("Produto cache " + UUID.randomUUID());
        product.setDescription("Produto para o teste de cache");
        product.setPrice(new BigDecimal("12.50"));
        product.setStock(stock);
        product.setActive(true);
        return productService.create(product, category.getId());
    }

    private ProductPageDTO listing(Product product) {
        return catalogReader.findPage(new ProductFilterDTO(product.getName(), null, null), FIRST_PAGE);
    }

    private void changeStockBypassingTheService(UUID productId, int stock) {
        Product product = productRepository.findByIdWithCategory(productId).orElseThrow();
        product.setStock(stock);
        productRepository.saveAndFlush(product);
    }

    private int keyCount(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys == null ? 0 : keys.size();
    }

    @Test
    void theFirstReadFillsRedisAndTheSecondOneIsServedFromIt() {
        Product product = newProduct(newCategory(), 10);

        assertThat(keyCount(LIST_KEY_PATTERN)).isZero();
        ProductPageDTO first = listing(product);
        assertThat(keyCount(LIST_KEY_PATTERN)).isEqualTo(1);

        changeStockBypassingTheService(product.getId(), 3);
        ProductPageDTO second = listing(product);

        assertThat(first.content().getFirst().stock()).isEqualTo(10);
        assertThat(second.content().getFirst().stock()).isEqualTo(10);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void theCachedValueIsPlainJsonAndSurvivesTheRoundTripWithoutLosingPrecision() {
        Product product = newProduct(newCategory(), 10);

        ProductPageDTO first = listing(product);
        String key = redisTemplate.keys(LIST_KEY_PATTERN).iterator().next();
        String json = redisTemplate.opsForValue().get(key);

        assertThat(key).startsWith("supermercado:productList::");
        assertThat(json).contains("\"totalElements\":1").contains(product.getName());
        ProductResponseDTO cached = listing(product).content().getFirst();
        assertThat(cached.price()).isEqualTo(new BigDecimal("12.50"));
        assertThat(cached).isEqualTo(first.content().getFirst());
        assertThat(cached.category().name()).startsWith("Categoria cache");
    }

    @Test
    void theEntryHasATimeToLiveAndExpiresOnItsOwn() {
        Product product = newProduct(newCategory(), 10);
        listing(product);
        String key = redisTemplate.keys(LIST_KEY_PATTERN).iterator().next();

        Long secondsToLive = redisTemplate.getExpire(key);
        assertThat(secondsToLive).isBetween(1L, 5L);

        changeStockBypassingTheService(product.getId(), 3);
        assertThat(listing(product).content().getFirst().stock()).isEqualTo(10);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertThat(listing(product).content().getFirst().stock()).isEqualTo(3));
    }

    @Test
    void differentFiltersAndPagesUseDifferentEntries() {
        Product product = newProduct(newCategory(), 10);

        catalogReader.findPage(new ProductFilterDTO(product.getName(), null, null), PageRequest.of(0, 20));
        catalogReader.findPage(new ProductFilterDTO(product.getName(), null, null), PageRequest.of(0, 10));
        catalogReader.findPage(new ProductFilterDTO(product.getName(), null, null), PageRequest.of(1, 20));
        catalogReader.findPage(new ProductFilterDTO("outra busca", null, null), PageRequest.of(0, 20));
        catalogReader.findPage(new ProductFilterDTO(product.getName(), null, null), PageRequest.of(0, 20));

        assertThat(keyCount(LIST_KEY_PATTERN)).isEqualTo(4);
    }

    @Test
    void theDetailIsCachedAndStaleUntilSomethingEvictsIt() {
        Product product = newProduct(newCategory(), 10);

        ProductResponseDTO first = catalogReader.findById(product.getId());
        assertThat(keyCount(DETAIL_KEY_PATTERN)).isEqualTo(1);

        changeStockBypassingTheService(product.getId(), 3);
        ProductResponseDTO second = catalogReader.findById(product.getId());

        assertThat(second.stock()).isEqualTo(10);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void aMissingProductIsNotCachedAndKeepsReportingNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> catalogReader.findById(unknown)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> catalogReader.findById(unknown)).isInstanceOf(NotFoundException.class);
        assertThat(keyCount(DETAIL_KEY_PATTERN)).isZero();
    }

    @Test
    void creatingAProductEvictsTheListings() {
        Category category = newCategory();
        Product first = newProduct(category, 10);
        ProductFilterDTO sharedPrefix = new ProductFilterDTO("Produto cache", category.getId(), null);

        ProductPageDTO before = catalogReader.findPage(sharedPrefix, FIRST_PAGE);
        assertThat(before.totalElements()).isEqualTo(1);
        assertThat(keyCount(LIST_KEY_PATTERN)).isEqualTo(1);

        Product second = newProduct(category, 4);

        assertThat(keyCount(LIST_KEY_PATTERN)).isZero();
        ProductPageDTO after = catalogReader.findPage(sharedPrefix, FIRST_PAGE);
        assertThat(after.totalElements()).isEqualTo(2);
        assertThat(after.content()).extracting(ProductResponseDTO::id).contains(first.getId(), second.getId());
    }

    @Test
    void updatingAProductEvictsTheListingAndTheDetail() {
        Product product = newProduct(newCategory(), 10);
        listing(product);
        catalogReader.findById(product.getId());
        assertThat(keyCount("supermercado:*")).isEqualTo(2);

        Product loaded = productService.findById(product.getId());
        loaded.setPrice(new BigDecimal("99.90"));
        productService.update(loaded, loaded.getCategory().getId());

        assertThat(keyCount("supermercado:*")).isZero();
        assertThat(catalogReader.findById(product.getId()).price()).isEqualTo(new BigDecimal("99.90"));
        assertThat(listing(product).content().getFirst().price()).isEqualTo(new BigDecimal("99.90"));
    }

    @Test
    void deactivatingAProductRemovesItFromTheCachedListingAndDetail() {
        Product product = newProduct(newCategory(), 10);
        assertThat(listing(product).totalElements()).isEqualTo(1);
        assertThat(catalogReader.findById(product.getId())).isNotNull();

        productService.changeActive(product.getId(), false);

        assertThat(listing(product).totalElements()).isZero();
        assertThatThrownBy(() -> catalogReader.findById(product.getId())).isInstanceOf(NotFoundException.class);

        productService.changeActive(product.getId(), true);

        assertThat(listing(product).totalElements()).isEqualTo(1);
        assertThat(catalogReader.findById(product.getId())).isNotNull();
    }

    @Test
    void deactivatingTheCategoryHidesItsProductsFromTheCachedListing() {
        Category category = newCategory();
        Product product = newProduct(category, 10);
        assertThat(listing(product).totalElements()).isEqualTo(1);
        assertThat(catalogReader.findById(product.getId())).isNotNull();

        categoryService.changeActive(category.getId(), false);

        assertThat(listing(product).totalElements()).isZero();
        assertThatThrownBy(() -> catalogReader.findById(product.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void renamingTheCategoryShowsTheNewNameInTheCachedListing() {
        Category category = newCategory();
        Product product = newProduct(category, 10);
        assertThat(listing(product).content().getFirst().category().name()).isEqualTo(category.getName());

        Category loaded = categoryService.findById(category.getId());
        String newName = "Categoria renomeada " + UUID.randomUUID();
        loaded.setName(newName);
        categoryService.update(loaded);

        assertThat(listing(product).content().getFirst().category().name()).isEqualTo(newName);
    }

    @Test
    void aPaidOrderEvictsTheCacheSoTheListingShowsTheNewStock() {
        Product product = newProduct(newCategory(), 10);
        assertThat(listing(product).content().getFirst().stock()).isEqualTo(10);
        assertThat(catalogReader.findById(product.getId()).stock()).isEqualTo(10);

        User user = new User();
        user.setKeycloakId(UUID.randomUUID());
        user.setName("Marta Oliveira");
        user.setEmail(UUID.randomUUID() + "@example.com");
        User buyer = userRepository.saveAndFlush(user);

        cartService.addItem(buyer, product.getId(), 3);
        UUID orderId = orderService.checkout(buyer).order().getId();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(listing(product).content().getFirst().stock()).isEqualTo(7);
            assertThat(catalogReader.findById(product.getId()).stock()).isEqualTo(7);
        });
    }

    @Test
    void aCancelledOrderDoesNotLeaveAStaleListingBehind() {
        Product product = newProduct(newCategory(), 5);
        assertThat(listing(product).content().getFirst().stock()).isEqualTo(5);

        User firstUser = userRepository.saveAndFlush(user());
        User secondUser = userRepository.saveAndFlush(user());
        cartService.addItem(firstUser, product.getId(), 4);
        cartService.addItem(secondUser, product.getId(), 4);
        UUID firstOrder;
        UUID secondOrder;
        listenerRegistry.stop();
        try {
            firstOrder = orderService.checkout(firstUser).order().getId();
            secondOrder = orderService.checkout(secondUser).order().getId();
        } finally {
            listenerRegistry.start();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OrderStatus first = orderRepository.findById(firstOrder).orElseThrow().getStatus();
            OrderStatus second = orderRepository.findById(secondOrder).orElseThrow().getStatus();
            assertThat(Set.of(first, second)).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.CANCELLED);
        });
        await().atMost(Duration.ofSeconds(2)).untilAsserted(
                () -> assertThat(listing(product).content().getFirst().stock()).isEqualTo(1));
    }

    private User user() {
        User user = new User();
        user.setKeycloakId(UUID.randomUUID());
        user.setName("Marta Oliveira");
        user.setEmail(UUID.randomUUID() + "@example.com");
        return user;
    }
}
