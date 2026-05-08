package com.xqt.saas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI xqtOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT");

        return new OpenAPI()
            .info(new Info()
                .title("新航线统一平台 API")
                .version("v1")
                .description("统一 SaaS、卖货客户履约、制单客户发货和后台权限接口"))
            .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
