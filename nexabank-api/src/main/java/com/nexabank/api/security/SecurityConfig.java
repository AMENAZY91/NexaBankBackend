package com.nexabank.api.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuración de Spring Security.
 *
 * <p>Rutas públicas (sin token válido requerido a nivel de framework):
 * <ul>
 *   <li>GET /actuator/health — health check</li>
 *   <li>/api/v1/auth/** — autenticación (login/registro/OTP/PIN)</li>
 *   <li>POST /api/v1/seed — seed de datos de demo (intencionalmente público, fuera de
 *       alcance de este cambio; ver docs/DECISIONS.md)</li>
 *   <li>/v3/api-docs/**, /swagger-ui/**, /swagger-ui.html — documentación OpenAPI</li>
 *   <li>GET /api/v1/qr/decode — decodificar QR sin pagar</li>
 *   <li>GET /api/v1/fx/rates/**, POST /api/v1/fx/convert — tasas FX públicas</li>
 *   <li>GET/POST /api/v1/benchmarks/** — demo académica de benchmarks</li>
 *   <li>POST /api/v1/webhooks/kyc — webhook de proveedor externo de KYC; no puede traer
 *       un token Firebase, se autentica con {@code WebhookSignatureVerifier} dentro del
 *       propio controller (hoy rechaza siempre — ver {@code NoOpWebhookSignatureVerifier})</li>
 * </ul>
 *
 * <p>Todo lo demás requiere un token Firebase válido ({@code anyRequest().authenticated()}).
 * La autorización por rol (ADMIN) se aplica a nivel de método con
 * {@code @PreAuthorize} (ver {@link com.nexabank.api.controller.AdminController}).
 *
 * <p>API stateless: CSRF deshabilitado, sin sesiones.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        this.firebaseAuthFilter = firebaseAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Usado para hashear/verificar el PIN del usuario (mejora #14). Fuerza de trabajo por
     * defecto (10 rondas) — suficiente para un PIN numérico corto en este proyecto académico.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF deshabilitado — API stateless
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Sin sesiones HTTP
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Reglas de autorización
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/seed").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/qr/decode").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/fx/rates/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/fx/convert").permitAll()
                        .requestMatchers("/api/v1/benchmarks/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/kyc").permitAll()

                        // Todo lo demás requiere autenticación Firebase válida
                        .anyRequest().authenticated()
                )

                // Sin esto, Spring Security devuelve 403 (no 401) para requests sin autenticar,
                // porque no hay form-login/basic-auth configurado que provea un
                // AuthenticationEntryPoint por defecto.
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedEntryPoint()))

                // Rate limiting antes de autenticación (se aplica incluso a requests sin token)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                // Registrar el filtro de Firebase antes del filtro estándar
                .addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Responde 401 (no 403) cuando no hay autenticación válida, con el mismo formato
     * JSON que {@code GlobalExceptionHandler}.
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required. Provide a valid Firebase ID token.\"}");
        };
    }
}
