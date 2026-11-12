package com.nexabank.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Filtro de rate limiting (token bucket) para rutas sensibles a abuso:
 * autenticación, transferencias y pagos QR.
 *
 * <p>Límite: 10 solicitudes/minuto por combinación (identificador de cliente + ruta protegida).
 * El identificador de cliente es la IP remota (primer valor de {@code X-Forwarded-For} si está
 * presente, o {@code request.getRemoteAddr()} en su defecto).
 *
 * <p>Cuando se excede el límite, responde {@code 429 Too Many Requests} con un cuerpo JSON simple
 * y no continúa la cadena de filtros.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.ratelimit.capacity:10}")
    private int capacity;

    @Value("${app.ratelimit.refill-period-seconds:60}")
    private long refillPeriodSeconds;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isProtected(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = clientId(request);
        String bucketKey = clientId + ":" + protectedGroup(path);
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // 429 Too Many Requests (not defined as a constant pre-Servlet 6.1)
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    private boolean isProtected(String path) {
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/transfers")
                || path.equals("/api/v1/qr/pay")
                || path.equals("/api/v1/topups")
                || path.equals("/api/v1/withdrawals")
                || path.startsWith("/api/v1/payment-requests")
                || path.equals("/api/v1/me/kyc")
                || path.equals("/api/v1/webhooks/kyc")
                || path.startsWith("/api/v1/cards")
                || path.equals("/api/v1/users/lookup");
    }

    private String protectedGroup(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.equals("/api/v1/transfers")) {
            return "transfers";
        }
        if (path.equals("/api/v1/topups")) {
            return "topups";
        }
        if (path.equals("/api/v1/withdrawals")) {
            return "withdrawals";
        }
        if (path.startsWith("/api/v1/payment-requests")) {
            return "payment-requests";
        }
        if (path.equals("/api/v1/me/kyc")) {
            return "kyc";
        }
        if (path.equals("/api/v1/webhooks/kyc")) {
            return "kyc-webhook";
        }
        if (path.startsWith("/api/v1/cards")) {
            return "cards";
        }
        if (path.equals("/api/v1/users/lookup")) {
            return "user-lookup";
        }
        return "qr-pay";
    }

    private String clientId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity,
                io.github.bucket4j.Refill.greedy(capacity, Duration.ofSeconds(refillPeriodSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }
}
