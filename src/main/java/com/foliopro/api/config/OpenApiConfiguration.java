package com.foliopro.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Folio Pro API",
                version = "v1",
                description = """
                        REST API for portfolio management, watchlists, and preferences.
                        Authentication and per-user
                        ownership are not implemented; do not expose this development API publicly.
                        """
        )
)
public class OpenApiConfiguration {}
