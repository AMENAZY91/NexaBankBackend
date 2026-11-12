package com.nexabank.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cubre específicamente la regresión encontrada al preparar el despliegue a Cloud Run
 * (Fase B, docs/FRONTEND_BACKEND_INTEGRATION_PLAN.md): con solo
 * {@code @Value("${app.cors.allowed-origins:}")}, la variable de entorno
 * {@code ALLOWED_ORIGINS} (documentada y usada en el deploy) NO resolvía la propiedad —
 * verificado empíricamente arrancando la app real en perfil {@code prod}. El fix usa
 * {@code @Value("${ALLOWED_ORIGINS:${app.cors.allowed-origins:}}")}; este test fija
 * {@code allowedOriginsProperty} directamente (lo que Spring habría resuelto en runtime)
 * para probar la lógica de {@code resolveAllowedOrigins()} sin levantar un contexto completo.
 */
@ExtendWith(MockitoExtension.class)
class CorsConfigTest {

    @Mock
    private Environment environment;

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig(environment);
    }

    @Test
    void shouldUseConfiguredOrigins_whenProdProfileAndOriginsSet() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsProperty",
                "https://nexabank-core.web.app,https://nexabank-core.firebaseapp.com");

        CorsConfiguration config = extractConfig(corsConfig);

        assertThat(config.getAllowedOriginPatterns())
                .containsExactly("https://nexabank-core.web.app", "https://nexabank-core.firebaseapp.com");
    }

    @Test
    void shouldReturnEmptyOrigins_whenProdProfileAndNotConfigured() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsProperty", "");

        CorsConfiguration config = extractConfig(corsConfig);

        assertThat(config.getAllowedOriginPatterns()).isEmpty();
    }

    @Test
    void shouldUsePermissiveDefaults_whenNotProdLike() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        CorsConfiguration config = extractConfig(corsConfig);

        assertThat(config.getAllowedOriginPatterns()).contains("*", "http://localhost:*");
    }

    @Test
    void shouldTrimAndIgnoreBlankEntries_inOriginsList() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsProperty",
                " https://nexabank-core.web.app , , https://nexabank-core.firebaseapp.com ");

        CorsConfiguration config = extractConfig(corsConfig);

        assertThat(config.getAllowedOriginPatterns())
                .containsExactly("https://nexabank-core.web.app", "https://nexabank-core.firebaseapp.com");
    }

    private CorsConfiguration extractConfig(CorsConfig corsConfig) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsConfig.corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }
}
