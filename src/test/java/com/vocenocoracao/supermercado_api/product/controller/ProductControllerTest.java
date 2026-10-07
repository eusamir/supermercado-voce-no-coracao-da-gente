package com.vocenocoracao.supermercado_api.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.config.SecurityConfig;
import com.vocenocoracao.supermercado_api.exceptions.GlobalExceptionHandler;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
import com.vocenocoracao.supermercado_api.product.controller.converter.ProductRequestDTOToProductConverter;
import com.vocenocoracao.supermercado_api.product.controller.converter.ProductToProductResponseDTOConverter;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductController.class)
@Import({
        SecurityConfig.class,
        ModelMapperConfig.class,
        GlobalExceptionHandler.class,
        ProductToProductResponseDTOConverter.class,
        ProductRequestDTOToProductConverter.class
})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String validBody(UUID categoryId) {
        return "{\"name\":\"Leite integral 1L\",\"description\":\"Leite UHT\",\"price\":5.29,\"stock\":10,\"categoryId\":\"" + categoryId + "\"}";
    }

    private Product product(UUID id) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Mercearia");

        Product product = new Product();
        product.setId(id);
        product.setName("Leite integral 1L");
        product.setDescription("Leite UHT integral");
        product.setPrice(new BigDecimal("5.29"));
        product.setStock(200);
        product.setActive(true);
        product.setCategory(category);
        return product;
    }

    @Test
    void listingIsPublicAndReturnsStockAndCategory() throws Exception {
        when(service.findAllActive(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product(UUID.randomUUID()))));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Leite integral 1L"))
                .andExpect(jsonPath("$.content[0].price").value(5.29))
                .andExpect(jsonPath("$.content[0].stock").value(200))
                .andExpect(jsonPath("$.content[0].category.name").value("Mercearia"));
    }

    @Test
    void listingBindsSearchAndCategoryFilters() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(service.findAllActive(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/products").param("search", "leite").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk());

        verify(service).findAllActive(
                argThat(filter -> "leite".equals(filter.search()) && categoryId.equals(filter.categoryId())),
                any());
    }

    @Test
    void listingAppliesTheDefaultAndMaximumPageSize() throws Exception {
        when(service.findAllActive(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products").param("size", "500")).andExpect(status().isOk());

        verify(service).findAllActive(any(), argThat(pageable -> pageable.getPageSize() == 20));
        verify(service).findAllActive(any(), argThat(pageable -> pageable.getPageSize() == 100));
    }

    @Test
    void listingWithInvalidCategoryIdIs400() throws Exception {
        mockMvc.perform(get("/api/products").param("categoryId", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void listingWithInvalidSortIs400() throws Exception {
        when(service.findAllActive(any(), any()))
                .thenThrow(new InvalidRequestException("Ordenação inválida. Campos permitidos: name, price."));

        mockMvc.perform(get("/api/products").param("sort", "stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ordenação inválida. Campos permitidos: name, price."));
    }

    @Test
    void detailIsPublic() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findActiveById(id)).thenReturn(product(id));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void detailOfHiddenProductIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findActiveById(id)).thenThrow(new NotFoundException("Produto não encontrado."));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Produto não encontrado."));
    }

    @Test
    void detailWithInvalidUuidIs400() throws Exception {
        mockMvc.perform(get("/api/products/{id}", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAsCustomerIs403() throws Exception {
        mockMvc.perform(post("/api/products").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAsAdminIs201() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(service.create(any(), eq(categoryId))).thenReturn(product(UUID.randomUUID()));

        mockMvc.perform(post("/api/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Leite integral 1L"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithInvalidFieldsIs400WithFieldErrors() throws Exception {
        String body = "{\"name\":\" \",\"price\":-1,\"stock\":-5}";

        mockMvc.perform(post("/api/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("O nome é obrigatório."))
                .andExpect(jsonPath("$.errors.price").value("O preço não pode ser negativo."))
                .andExpect(jsonPath("$.errors.stock").value("O estoque não pode ser negativo."))
                .andExpect(jsonPath("$.errors.categoryId").value("A categoria é obrigatória."));
    }

    @Test
    void createWithMoreThanTwoDecimalPlacesIs400() throws Exception {
        String body = "{\"name\":\"Leite\",\"price\":5.299,\"stock\":1,\"categoryId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").value("O preço deve ter no máximo 2 casas decimais."));
    }

    @Test
    void createWithUnknownCategoryIs404() throws Exception {
        when(service.create(any(), any())).thenThrow(new NotFoundException("Categoria não encontrada."));

        mockMvc.perform(post("/api/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithInactiveCategoryIs400() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new InvalidRequestException("Não é possível usar uma categoria inativa."));

        mockMvc.perform(post("/api/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAsAdminAppliesTheNewValues() throws Exception {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(service.findById(id)).thenReturn(product(id));
        when(service.update(any(), eq(categoryId))).thenAnswer(invocation -> invocation.getArgument(0));

        String body = "{\"name\":\"Leite desnatado 1L\",\"price\":6.10,\"stock\":7,\"categoryId\":\"" + categoryId + "\"}";

        mockMvc.perform(put("/api/products/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Leite desnatado 1L"))
                .andExpect(jsonPath("$.price").value(6.10))
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void updateAsCustomerIs403() throws Exception {
        mockMvc.perform(put("/api/products/{id}", UUID.randomUUID()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOfMissingProductIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new NotFoundException("Produto não encontrado."));

        mockMvc.perform(put("/api/products/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithStaleVersionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(product(id));
        when(service.update(any(), any())).thenThrow(new ObjectOptimisticLockingFailureException(Product.class, id));

        mockMvc.perform(put("/api/products/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void changeActiveAsAdminIs200() throws Exception {
        UUID id = UUID.randomUUID();
        Product inactive = product(id);
        inactive.setActive(false);
        when(service.changeActive(id, false)).thenReturn(inactive);

        mockMvc.perform(patch("/api/products/{id}/active", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void changeActiveWithoutBodyFieldIs400() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/active", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeActiveAsCustomerIs403() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/active", UUID.randomUUID()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListingRequiresAdmin() throws Exception {
        when(service.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(product(UUID.randomUUID()))));

        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/products").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/products").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Leite integral 1L"));
    }

    @Test
    void adminListingBindsTheActiveFilter() throws Exception {
        when(service.findAll(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/products").with(admin()).param("active", "false")).andExpect(status().isOk());

        verify(service).findAll(argThat(filter -> Boolean.FALSE.equals(filter.active())), any());
    }

    @Test
    void adminDetailRequiresAdminAndShowsInactiveProducts() throws Exception {
        UUID id = UUID.randomUUID();
        Product inactive = product(id);
        inactive.setActive(false);
        when(service.findById(id)).thenReturn(inactive);

        mockMvc.perform(get("/api/admin/products/{id}", id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/products/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
