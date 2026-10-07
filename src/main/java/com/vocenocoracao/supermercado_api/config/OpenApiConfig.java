package com.vocenocoracao.supermercado_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final List<String> TAG_ORDER = List.of("Usuários", "Produtos", "Categorias", "Carrinho", "Pedidos");
    private static final String PROBLEM_DETAIL = "ProblemDetail";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Supermercado Você no Coração da Gente - API")
                        .version("1.0.0")
                        .description("API do fluxo de compras do supermercado: cadastro, catálogo, carrinho, checkout, pagamento assíncrono e consulta de pedidos."))
                .tags(List.of(
                        new Tag().name("Usuários").description("Cadastro e perfil do usuário logado."),
                        new Tag().name("Produtos").description("Catálogo público e administração de produtos."),
                        new Tag().name("Categorias").description("Consulta de categorias e administração."),
                        new Tag().name("Carrinho").description("Carrinho do usuário logado."),
                        new Tag().name("Pedidos").description("Checkout, pagamento e consulta de pedidos.")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT emitido pelo Keycloak.")));
    }

    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> openApi.setTags(openApi.getTags().stream()
                .sorted(Comparator.comparingInt(tag -> TAG_ORDER.indexOf(tag.getName())))
                .collect(Collectors.toList()));
    }

    @Bean
    public OpenApiCustomizer problemDetailCustomizer() {
        return openApi -> {
            Schema<?> problemDetail = new ObjectSchema()
                    .addProperty("type", new StringSchema().example("about:blank"))
                    .addProperty("title", new StringSchema().example("Recurso não encontrado"))
                    .addProperty("status", new IntegerSchema().example(404))
                    .addProperty("detail", new StringSchema().example("Pedido não encontrado."))
                    .addProperty("instance", new StringSchema().example("/api/orders/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                    .addProperty("errors", new ObjectSchema()
                            .additionalProperties(new StringSchema())
                            .description("Presente apenas em erros de validação: campo e mensagem."));

            openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetail);

            Content problemContent = new Content().addMediaType(
                    PROBLEM_MEDIA_TYPE,
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))
            );

            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation ->
                    operation.getResponses().forEach((statusCode, response) -> {
                        boolean isError = statusCode.startsWith("4") || statusCode.startsWith("5");
                        if (isError) {
                            response.setContent(problemContent);
                        }
                    })));
        };
    }
}
