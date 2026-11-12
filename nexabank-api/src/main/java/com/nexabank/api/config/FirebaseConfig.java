package com.nexabank.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final Environment environment;

    @Value("${firebase.project-id:}")
    private String projectId;

    @Value("${firebase.client-email:}")
    private String clientEmail;

    @Value("${firebase.private-key:}")
    private String privateKey;

    @Value("${FIREBASE_PRIVATE_KEY_ID:}")
    private String privateKeyId;

    @Value("${FIREBASE_CLIENT_ID:}")
    private String clientId;

    @Value("${FIREBASE_TOKEN_URI:https://oauth2.googleapis.com/token}")
    private String tokenUri;

    public FirebaseConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            if (isEmulatorMode()) {
                String emulatorProjectId = firstPresent(
                        environment.getProperty("FIREBASE_PROJECT_ID"),
                        System.getenv("FIREBASE_PROJECT_ID"),
                        "demo-nexabank-test");
                FirebaseOptions emulatorOptions = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.create(new AccessToken("owner", null)))
                        .setProjectId(emulatorProjectId)
                        .build();
                FirebaseApp.initializeApp(emulatorOptions);
                log.info("Firebase initialized against local emulators for project: {}", emulatorProjectId);
                return;
            }

            hydrateFromDotEnvIfNeeded();

            FirebaseOptions options;
            InputStream credentialsStream = loadCredentialsStream();

            if (credentialsStream != null) {
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                        .build();
                log.info("Firebase initialized from credentials file");
            } else if (hasServiceAccountEnv()) {
                String serviceAccountJson = buildServiceAccountJson();
                InputStream stream = new ByteArrayInputStream(
                        serviceAccountJson.getBytes(StandardCharsets.UTF_8));

                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .setProjectId(projectId)
                        .build();
                log.info("Firebase initialized from environment variables");
            } else {
                throw new IllegalStateException(
                        "Firebase credentials missing. Set FIREBASE_PROJECT_ID, "
                                + "FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY, "
                                + "FIREBASE_PRIVATE_KEY_ID and FIREBASE_CLIENT_ID in nexabank-api/.env");
            }

            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully for project: {}",
                    FirebaseApp.getInstance().getOptions().getProjectId());
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }

    private boolean isEmulatorMode() {
        return isPresent(firstPresent(environment.getProperty("FIRESTORE_EMULATOR_HOST"), System.getenv("FIRESTORE_EMULATOR_HOST")))
                && isPresent(firstPresent(environment.getProperty("FIREBASE_AUTH_EMULATOR_HOST"), System.getenv("FIREBASE_AUTH_EMULATOR_HOST")));
    }

    private boolean hasServiceAccountEnv() {
        return isPresent(projectId)
                && isPresent(clientEmail)
                && isPresent(privateKey)
                && isPresent(privateKeyId)
                && isPresent(clientId);
    }

    private void hydrateFromDotEnvIfNeeded() throws IOException {
        applyCredentialValues(null);

        if (hasServiceAccountEnv()) {
            return;
        }

        for (Path candidate : dotenvCandidates()) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            log.info("Loading Firebase credentials from {}", candidate.toAbsolutePath());
            applyCredentialValues(parseDotEnv(candidate));
            if (hasServiceAccountEnv()) {
                return;
            }
        }
    }

    private void applyCredentialValues(Map<String, String> values) {
        projectId = firstPresent(projectId, from(values, "FIREBASE_PROJECT_ID"), propertyOrEnv("FIREBASE_PROJECT_ID"));
        clientEmail = firstPresent(clientEmail, from(values, "FIREBASE_CLIENT_EMAIL"), propertyOrEnv("FIREBASE_CLIENT_EMAIL"));
        privateKey = firstPresent(privateKey, from(values, "FIREBASE_PRIVATE_KEY"), propertyOrEnv("FIREBASE_PRIVATE_KEY"));
        privateKeyId = firstPresent(privateKeyId, from(values, "FIREBASE_PRIVATE_KEY_ID"), propertyOrEnv("FIREBASE_PRIVATE_KEY_ID"));
        clientId = firstPresent(clientId, from(values, "FIREBASE_CLIENT_ID"), propertyOrEnv("FIREBASE_CLIENT_ID"));
        tokenUri = firstPresent(tokenUri, from(values, "FIREBASE_TOKEN_URI"), propertyOrEnv("FIREBASE_TOKEN_URI"));
    }

    private String from(Map<String, String> values, String key) {
        return values == null ? null : values.get(key);
    }

    private String propertyOrEnv(String key) {
        return firstPresent(environment.getProperty(key), System.getenv(key));
    }

    private List<Path> dotenvCandidates() {
        Path cwd = Path.of("").toAbsolutePath();
        return List.of(
                cwd.resolve(".env"),
                cwd.resolve("nexabank-api/.env"),
                Path.of("nexabank-api/.env"),
                Path.of(".env")
        );
    }

    private InputStream loadCredentialsStream() throws IOException {
        ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
        if (resource.exists()) {
            log.info("Loading Firebase credentials from classpath");
            return resource.getInputStream();
        }
        return null;
    }

    @Bean
    public Firestore firestore() {
        return FirestoreClient.getFirestore();
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }

    private String buildServiceAccountJson() throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("type", "service_account");
        payload.put("project_id", projectId);
        payload.put("private_key_id", privateKeyId);
        payload.put("private_key", toPem(privateKey));
        payload.put("client_email", clientEmail);
        payload.put("client_id", clientId);
        payload.put("token_uri", firstPresent(tokenUri, DEFAULT_TOKEN_URI));
        return new ObjectMapper().writeValueAsString(payload);
    }

    static String toPem(String rawKey) {
        return stripWrappingQuotes(rawKey).replace("\\n", "\n").replace("\r", "");
    }

    static Map<String, String> parseDotEnv(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = stripWrappingQuotes(trimmed.substring(separator + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String firstPresent(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (isPresent(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
