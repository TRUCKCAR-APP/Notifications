package com.truckcar.notifications.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationsOpenAPI() {
        String bearerSchemeName = "bearerAuth";
        String apiKeySchemeName = "apiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("TruckCar - Notifications Microservice API")
                        .description("Microservicio de entrega de notificaciones (Push vía SNS y Voz vía Polly + S3) para TruckCar")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo TruckCar")
                                .email("soporte@truckcar.co")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerSchemeName)
                        .addList(apiKeySchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(apiKeySchemeName, new SecurityScheme()
                                .name("X-API-KEY")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)));
    }
}
