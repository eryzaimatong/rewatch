package com.rewatch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Generated spec, not hand-written — springdoc reads it straight off the
 * existing controllers and DTOs (request/response shapes, path variables,
 * @Valid constraints), so this class only supplies what it can't infer:
 * top-level metadata and the JWT bearer scheme, so "Authorize" in the UI
 * actually works against endpoints gated by SecurityConfig.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI rewatchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Re:Watch API")
                        .description(
                                "Emotional-storytelling recommendation engine. Most endpoints require a "
                                        + "JWT (POST /api/auth/login or /api/auth/register, then Authorize below "
                                        + "with the returned token). See docs/CASE-STUDY.md in the repo for how "
                                        + "the trait model and scoring actually work.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
