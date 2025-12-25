package com.socialwallet.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Social Wallet API",
        version = "1.0.0",
        description = "API REST pour l'application Social Wallet : messagerie privée, wallet et store (modèle WhatsApp-like)."
    ),
    security = @SecurityRequirement(name = "bearerAuth")  // ← Ligne magique qui fait apparaître le bouton
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT Authorization header using the Bearer scheme.\r\n\r\n" +
                 "Enter 'Bearer' [space] and then your token in the text input below.\r\n\r\n" +
                 "Example: \"Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.xxxxx\"",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}