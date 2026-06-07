package com.hemanth.orderprocessingsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata and JWT bearer authentication configuration.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * Configures Swagger UI to expose the Authorize button for JWT bearer tokens.
     */
    @Bean
    public OpenAPI orderProcessingOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Order Processing System")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme));
    }
}
