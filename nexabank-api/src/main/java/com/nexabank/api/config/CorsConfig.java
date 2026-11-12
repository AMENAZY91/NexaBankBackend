package com.nexabank.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuración de CORS por entorno.
 *
 * <p>En {@code prod}/{@code staging}, los orígenes permitidos vienen exclusivamente de
 * {@code ALLOWED_ORIGINS} (lista separada por comas, ej. el dominio real de Firebase Hosting
 * del frontend) — nunca wildcard. En {@code dev}/{@code test} se mantiene el comportamiento
 * histórico (wildcard + localhost + ngrok) para facilitar pruebas locales/dispositivo físico.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final Environment environment;

    /**
     * {@code ALLOWED_ORIGINS} se resuelve explícitamente primero (no solo vía el binding
     * relajado de Spring) — con solo {@code app.cors.allowed-origins}, la variable de
     * entorno que hay que exportar sería {@code APP_CORS_ALLOWED_ORIGINS}, no
     * {@code ALLOWED_ORIGINS} como documenta esta misma clase (y como se configura en
     * Cloud Run) — verificado empíricamente: exportar solo {@code ALLOWED_ORIGINS} con el
     * binding implícito NO resolvía la propiedad y CORS rechazaba todo en perfil prod.
     */
    @Value("${ALLOWED_ORIGINS:${app.cors.allowed-origins:}}")
    private String allowedOriginsProperty;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(resolveAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        boolean isProdLike = isActiveProfile("prod") || isActiveProfile("staging");

        if (!isProdLike) {
            return Arrays.asList(
                    "*",
                    "http://localhost:*",
                    "https://localhost:*",
                    "https://*.ngrok-free.dev",
                    "https://*.ngrok.io"
            );
        }

        List<String> origins = new ArrayList<>();
        if (allowedOriginsProperty != null && !allowedOriginsProperty.isBlank()) {
            for (String origin : allowedOriginsProperty.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }

        if (origins.isEmpty()) {
            log.error("ALLOWED_ORIGINS is not configured in a prod-like profile; CORS will reject all cross-origin requests");
        }

        return origins;
    }

    private boolean isActiveProfile(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
