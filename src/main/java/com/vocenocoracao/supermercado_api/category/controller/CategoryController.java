package com.vocenocoracao.supermercado_api.category.controller;

import com.vocenocoracao.supermercado_api.category.dto.CategoryActiveDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryFilterDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryRequestDTO;
import com.vocenocoracao.supermercado_api.category.dto.CategoryResponseDTO;
import com.vocenocoracao.supermercado_api.category.entity.Category;
import com.vocenocoracao.supermercado_api.category.service.CategoryService;
import com.vocenocoracao.supermercado_api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Categorias", description = "Consulta de categorias e administração.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class CategoryController {
    private final CategoryService categoryService;
    private final ModelMapper modelMapper;

    public CategoryController(CategoryService categoryService, ModelMapper modelMapper) {
        this.categoryService = categoryService;
        this.modelMapper = modelMapper;
    }

    @Operation(summary = "Listar categorias ativas", description = "Requer login. Só categorias ativas, paginadas, com filtros opcionais por nome (search) e datas de criação.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido.")
    })
    @GetMapping("/api/categories")
    public Page<CategoryResponseDTO> findAllActive(
            @ParameterObject CategoryFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return categoryService.findAllActive(filter, pageable).map(this::toResponse);
    }

    @Operation(summary = "Detalhar categoria ativa", description = "Requer login. Categoria inativa é tratada como não encontrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    })
    @GetMapping("/api/categories/{id}")
    public CategoryResponseDTO findActiveById(@PathVariable UUID id) {
        return toResponse(categoryService.findActiveById(id));
    }

    @Operation(summary = "Listar categorias (administração)", description = "Requer papel ADMIN. Inclui categorias inativas e aceita o filtro active.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador.")
    })
    @GetMapping("/api/admin/categories")
    public Page<CategoryResponseDTO> findAll(
            @ParameterObject CategoryFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return categoryService.findAll(filter, pageable).map(this::toResponse);
    }

    @Operation(summary = "Detalhar categoria (administração)", description = "Requer papel ADMIN. Devolve a categoria mesmo se estiver inativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    })
    @GetMapping("/api/admin/categories/{id}")
    public CategoryResponseDTO findById(@PathVariable UUID id) {
        return toResponse(categoryService.findById(id));
    }

    @Operation(summary = "Criar categoria", description = "Requer papel ADMIN. O nome é único, sem diferenciar maiúsculas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "409", description = "Categoria já cadastrada.")
    })
    @PostMapping("/api/categories")
    public ResponseEntity<CategoryResponseDTO> create(@Validated @RequestBody CategoryRequestDTO request) {
        Category category = categoryService.create(modelMapper.map(request, Category.class));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @Operation(summary = "Renomear categoria", description = "Requer papel ADMIN. O novo nome não pode pertencer a outra categoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada."),
            @ApiResponse(responseCode = "409", description = "Categoria já cadastrada.")
    })
    @PutMapping("/api/categories/{id}")
    public CategoryResponseDTO update(
            @PathVariable UUID id,
            @Validated @RequestBody CategoryRequestDTO request
    ) {
        Category category = categoryService.findById(id);
        modelMapper.map(request, category);
        return toResponse(categoryService.update(category));
    }

    @Operation(summary = "Ativar ou desativar categoria", description = "Requer papel ADMIN. A operação é idempotente e a categoria nunca é apagada.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Corpo inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    })
    @PatchMapping("/api/categories/{id}/active")
    public CategoryResponseDTO changeActive(
            @PathVariable UUID id,
            @Validated @RequestBody CategoryActiveDTO request
    ) {
        return toResponse(categoryService.changeActive(id, request.active()));
    }

    private CategoryResponseDTO toResponse(Category category) {
        return modelMapper.map(category, CategoryResponseDTO.class);
    }
}
