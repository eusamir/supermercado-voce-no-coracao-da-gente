package com.vocenocoracao.supermercado_api.product.controller;

import com.vocenocoracao.supermercado_api.config.OpenApiConfig;
import com.vocenocoracao.supermercado_api.product.dto.ProductActiveDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductFilterDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductPageDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductRequestDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import com.vocenocoracao.supermercado_api.product.entity.Product;
import com.vocenocoracao.supermercado_api.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

@Tag(name = "Produtos", description = "Catálogo público e administração de produtos.")
@RestController
public class ProductController {
    private final ProductService productService;
    private final ProductCatalogReader productCatalogReader;
    private final ModelMapper modelMapper;

    public ProductController(
            ProductService productService,
            ProductCatalogReader productCatalogReader,
            ModelMapper modelMapper
    ) {
        this.productService = productService;
        this.productCatalogReader = productCatalogReader;
        this.modelMapper = modelMapper;
    }

    @Operation(summary = "Listar produtos", description = "Público. Só produtos ativos de categorias ativas, paginados (20 por página, máximo 100). Filtros: search (parte do nome, sem diferenciar maiúsculas) e categoryId. A ordenação aceita apenas name e price, com name como padrão.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Filtro ou ordenação inválidos.")
    })
    @GetMapping("/api/products")
    public Page<ProductResponseDTO> findAllActive(
            @ParameterObject ProductFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        ProductPageDTO page = productCatalogReader.findPage(filter, pageable);
        return new PageImpl<>(page.content(), pageable, page.totalElements());
    }

    @Operation(summary = "Detalhar produto", description = "Público. Produto inativo, ou de categoria inativa, é tratado como não encontrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.")
    })
    @GetMapping("/api/products/{id}")
    public ProductResponseDTO findActiveById(@PathVariable UUID id) {
        return productCatalogReader.findById(id);
    }

    @Operation(summary = "Listar produtos (administração)", description = "Requer papel ADMIN. Inclui produtos inativos e aceita o filtro active, além de search e categoryId.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Filtro ou ordenação inválidos."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/api/admin/products")
    public Page<ProductResponseDTO> findAll(
            @ParameterObject ProductFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        return productService.findAll(filter, pageable).map(this::toResponse);
    }

    @Operation(summary = "Detalhar produto (administração)", description = "Requer papel ADMIN. Devolve o produto mesmo se estiver inativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Identificador inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/api/admin/products/{id}")
    public ProductResponseDTO findById(@PathVariable UUID id) {
        return toResponse(productService.findById(id));
    }

    @Operation(summary = "Criar produto", description = "Requer papel ADMIN. O produto nasce ativo e a categoria precisa existir e estar ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou categoria inativa."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/products")
    public ResponseEntity<ProductResponseDTO> create(@Validated @RequestBody ProductRequestDTO request) {
        Product product = productService.create(modelMapper.map(request, Product.class), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @Operation(summary = "Editar produto", description = "Requer papel ADMIN. Substitui nome, descrição, preço, estoque e categoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou categoria inativa."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Produto ou categoria não encontrados."),
            @ApiResponse(responseCode = "409", description = "O produto foi alterado por outra requisição. Tente novamente.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PutMapping("/api/products/{id}")
    public ProductResponseDTO update(
            @PathVariable UUID id,
            @Validated @RequestBody ProductRequestDTO request
    ) {
        Product product = productService.findById(id);
        modelMapper.map(request, product);
        return toResponse(productService.update(product, request.categoryId()));
    }

    @Operation(summary = "Ativar ou desativar produto", description = "Requer papel ADMIN. A operação é idempotente e o produto nunca é apagado.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Corpo inválido."),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido."),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador."),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PatchMapping("/api/products/{id}/active")
    public ProductResponseDTO changeActive(
            @PathVariable UUID id,
            @Validated @RequestBody ProductActiveDTO request
    ) {
        return toResponse(productService.changeActive(id, request.active()));
    }

    private ProductResponseDTO toResponse(Product product) {
        return modelMapper.map(product, ProductResponseDTO.class);
    }
}
