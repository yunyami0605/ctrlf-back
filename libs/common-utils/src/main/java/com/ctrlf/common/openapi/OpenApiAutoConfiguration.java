package com.ctrlf.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

    private static final String BEARER_KEY = "bearer-jwt";

    @Bean
    public OpenAPI commonOpenAPI(
        @Value("${app.api.title:API}") String title,
        @Value("${app.api.description:}") String description,
        @Value("${app.api.security.enabled:true}") boolean securityEnabled
    ) {
        OpenAPI openAPI = new OpenAPI()
            .info(new Info().title(title).description(description));

        if (securityEnabled) {
            openAPI
                .components(new Components().addSecuritySchemes(BEARER_KEY,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_KEY));
        }
        return openAPI;
    }
}


