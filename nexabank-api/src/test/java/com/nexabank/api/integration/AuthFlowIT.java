package com.nexabank.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexabank.api.dto.auth.AuthResponse;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.model.PhoneOtp;
import com.nexabank.api.repository.PhoneOtpRepository;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.otp.LoggingOtpSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Recorre el flujo HTTP real de alta de usuario nuevo contra el Firebase Local Emulator
 * Suite (Auth + Firestore) — sin mocks de Firebase. Es la "prueba de fuego" automatizada:
 * si cualquiera de los pasos vuelve a quedar simulado (OTP no validado, token
 * {@code demo_token_...}, etc.), este test falla de inmediato.
 *
 * <p>Requiere que {@code FIRESTORE_EMULATOR_HOST} y {@code FIREBASE_AUTH_EMULATOR_HOST}
 * estén exportados en el proceso antes de que arranque el contexto Spring — los exporta
 * automáticamente {@code firebase emulators:exec --only auth,firestore "mvn verify"}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LoggingOtpSender otpSender;

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private ConfirmationTokenService confirmationTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useApacheHttpClient() {
        // El SimpleClientHttpRequestFactory basado en HttpURLConnection lanza
        // "cannot retry due to server authentication, in streaming mode" en POSTs
        // que reciben 401/429 — un bug conocido del JDK, no de este servicio.
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void newUserCanRegister_setPin_andReachProtectedEndpoints() throws Exception {
        String phone = uniquePhone();

        ResponseEntity<AuthResponse> start = restTemplate.postForEntity(
                "/api/v1/auth/phone/start", Map.of("phone", phone), AuthResponse.class);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);

        String code = otpSender.getLastCodeFor(phone);
        assertThat(code).matches("\\d{6}");

        ResponseEntity<AuthResponse> verify = restTemplate.postForEntity(
                "/api/v1/auth/phone/verify", Map.of("phone", phone, "code", code), AuthResponse.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verify.getBody().getRequiresRegistration()).isTrue();

        Map<String, Object> registerBody = registerBodyFor(phone);
        ResponseEntity<AuthResponse> register = restTemplate.postForEntity(
                "/api/v1/auth/register", registerBody, AuthResponse.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.OK);
        String customToken = register.getBody().getToken();
        assertThat(customToken).isNotBlank();

        String idToken = exchangeCustomTokenForIdToken(customToken);
        assertThat(idToken).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + idToken);

        ResponseEntity<AuthResponse> pinSetup = restTemplate.postForEntity(
                "/api/v1/auth/pin/setup", new HttpEntity<>(Map.of("pin", "5678"), authHeaders), AuthResponse.class);
        assertThat(pinSetup.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AuthResponse> pinVerify = restTemplate.postForEntity(
                "/api/v1/auth/pin/verify", new HttpEntity<>(Map.of("pin", "5678"), authHeaders), AuthResponse.class);
        assertThat(pinVerify.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pinVerify.getBody().getConfirmationToken()).isNotBlank();

        ResponseEntity<AuthResponse> me = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders), AuthResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().getUser().getPhone()).isEqualTo(phone);
    }

    @Test
    void phoneVerify_shouldReject_wrongOtpCode() {
        String phone = uniquePhone();
        restTemplate.postForEntity("/api/v1/auth/phone/start", Map.of("phone", phone), AuthResponse.class);

        ResponseEntity<AuthResponse> verify = restTemplate.postForEntity(
                "/api/v1/auth/phone/verify", Map.of("phone", phone, "code", "000000"), AuthResponse.class);

        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void phoneVerify_shouldReject_expiredOtp() {
        String phone = uniquePhone();
        phoneOtpRepository.save(PhoneOtp.builder()
                .phone(phone)
                .codeHash(passwordEncoder.encode("123456"))
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .createdAt(Instant.now().minus(6, ChronoUnit.MINUTES))
                .build());

        ResponseEntity<AuthResponse> verify = restTemplate.postForEntity(
                "/api/v1/auth/phone/verify", Map.of("phone", phone, "code", "123456"), AuthResponse.class);

        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_shouldReject_duplicatePhone() {
        String phone = uniquePhone();
        Map<String, Object> body = registerBodyFor(phone);

        ResponseEntity<AuthResponse> first = restTemplate.postForEntity("/api/v1/auth/register", body, AuthResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AuthResponse> second = restTemplate.postForEntity("/api/v1/auth/register", body, AuthResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void protectedEndpoint_shouldReject_missingToken() {
        ResponseEntity<AuthResponse> me = restTemplate.getForEntity("/api/v1/auth/me", AuthResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void confirmationToken_shouldReject_whenReused() {
        String token = confirmationTokenService.generate("USR-IT-TEST");

        confirmationTokenService.validateAndConsume(token, "USR-IT-TEST");

        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume(token, "USR-IT-TEST"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }

    private Map<String, Object> registerBodyFor(String phone) {
        return Map.of(
                "phone", phone,
                "firstName", "Juan",
                "lastName", "Perez",
                "documentId", "CC-" + UUID.randomUUID(),
                "address", "Calle 123",
                "country", "CO",
                "email", "user" + System.nanoTime() + "@example.com",
                "displayName", "Juan Perez",
                "pin", "1234");
    }

    private String exchangeCustomTokenForIdToken(String customToken) throws Exception {
        String authEmulatorHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        String url = "http://" + authEmulatorHost
                + "/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=fake-api-key";

        String body = "{\"token\":\"" + customToken + "\",\"returnSecureToken\":true}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        Map<?, ?> json = new ObjectMapper().readValue(response.body(), Map.class);
        return (String) json.get("idToken");
    }

    private String uniquePhone() {
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 10_000_000_000L;
        return "+1" + String.format("%010d", suffix);
    }
}
