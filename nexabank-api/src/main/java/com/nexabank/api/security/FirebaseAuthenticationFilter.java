package com.nexabank.api.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Filtro de autenticación que verifica tokens de Firebase Authentication.
 *
 * <p>Flujo:
 * <pre>
 * Frontend → Firebase Auth → ID Token → Authorization: Bearer TOKEN → Spring Boot
 *     → Firebase Admin SDK → Token válido → SecurityContext
 * </pre>
 *
 * <p>Extrae el UID del usuario y lo establece como principal en el SecurityContext.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
                String uid = decodedToken.getUid();
                String email = decodedToken.getEmail();

                // Extraer roles de custom claims si existen
                List<SimpleGrantedAuthority> authorities = extractAuthorities(decodedToken);

                // Crear Authentication con UID como principal y email como credentials
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(uid, email, authorities);

                // Guardar el token decodificado como detalle para acceso adicional
                authentication.setDetails(decodedToken);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: uid={}, email={}", uid, email);

            } catch (FirebaseAuthException e) {
                log.warn("Invalid Firebase token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae authorities de los custom claims del token Firebase.
     */
    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractAuthorities(FirebaseToken token) {
        Map<String, Object> claims = token.getClaims();

        if (claims.containsKey("roles")) {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> roles) {
                return roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()))
                        .toList();
            }
        }

        // Default: ROLE_USER
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
