package com.vocenocoracao.supermercado_api.category.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vocenocoracao.supermercado_api.category.controller.converter.CategoryRequestDTOToCategoryConverter;
import com.vocenocoracao.supermercado_api.category.controller.converter.CategoryToCategoryResponseDTOConverter;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.config.ModelMapperConfig;
import com.vocenocoracao.supermercado_api.config.SecurityConfig;
import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.GlobalExceptionHandler;
import com.vocenocoracao.supermercado_api.exceptions.NotFoundException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CategoryController.class)
@Import({
        SecurityConfig.class,
        ModelMapperConfig.class,
        GlobalExceptionHandler.class,
        CategoryRequestDTOToCategoryConverter.class,
        CategoryToCategoryResponseDTOConverter.class
})
class CategoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CategoryService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private Category category(UUID id, String name, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setActive(active);
        return category;
    }

    @Test
    void listingWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void listingAsCustomerReturnsActiveCategories() throws Exception {
        when(service.findAllActive(any(), any()))
                .thenReturn(new PageImpl<>(List.of(category(UUID.randomUUID(), "Padaria", true))));

        mvc.perform(get("/api/categories").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Padaria"));
    }

    @Test
    void detailWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/categories/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void detailAsCustomerIs200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findActiveById(id)).thenReturn(category(id, "Padaria", true));

        mvc.perform(get("/api/categories/{id}", id).with(customer())).andExpect(status().isOk());
    }

    @Test
    void detailOfInactiveIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findActiveById(id)).thenThrow(new NotFoundException("Categoria não encontrada."));

        mvc.perform(get("/api/categories/{id}", id).with(customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria não encontrada."));
    }

    @Test
    void invalidUuidIs400() throws Exception {
        mvc.perform(get("/api/categories/{id}", "abc").with(customer())).andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Padaria\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAsCustomerIs403() throws Exception {
        mvc.perform(post("/api/categories").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Padaria\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAsAdminIs201() throws Exception {
        when(service.create(any())).thenReturn(category(UUID.randomUUID(), "Padaria", true));

        mvc.perform(post("/api/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Padaria\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Padaria"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithBlankNameIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("O nome é obrigatório."));
    }

    @Test
    void createWithMalformedJsonIs400() throws Exception {
        mvc.perform(post("/api/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDuplicateIs409() throws Exception {
        when(service.create(any())).thenThrow(new AlreadyExistsException("Categoria já cadastrada."));

        mvc.perform(post("/api/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Padaria\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Categoria já cadastrada."));
    }

    @Test
    void updateAsAdminRenamesCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(category(id, "Padaria", true));
        when(service.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(put("/api/categories/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pães\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pães"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void changeActiveAsAdminIs200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.changeActive(eq(id), eq(false))).thenReturn(category(id, "Padaria", false));

        mvc.perform(patch("/api/categories/{id}/active", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void changeActiveWithoutBodyFieldIs400() throws Exception {
        mvc.perform(patch("/api/categories/{id}/active", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeActiveAsCustomerIs403() throws Exception {
        mvc.perform(patch("/api/categories/{id}/active", UUID.randomUUID()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListingRequiresAdmin() throws Exception {
        when(service.findAll(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/categories").with(customer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/categories").with(admin())).andExpect(status().isOk());
    }

    @Test
    void adminDetailRequiresAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(category(id, "Padaria", false));

        mvc.perform(get("/api/admin/categories/{id}", id).with(customer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/categories/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
