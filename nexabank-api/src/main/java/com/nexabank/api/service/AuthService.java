package com.nexabank.api.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.nexabank.api.dto.auth.AuthResponse;
import com.nexabank.api.exception.AuthenticationRequiredException;
import com.nexabank.api.exception.DuplicateUserException;
import com.nexabank.api.exception.InvalidOtpException;
import com.nexabank.api.exception.PinLockedException;
import com.nexabank.api.dto.user.RegisterRequest;
import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.mapper.UserMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.PhoneOtp;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.PhoneOtpRepository;
import com.nexabank.api.repository.UserRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.service.otp.OtpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación según FlujoAuth.md.
 *
 * <p>Flujo:
 * <ol>
 *   <li>phone/start → genera y envía OTP real (vía {@link OtpSender})</li>
 *   <li>phone/verify → valida el OTP contra el hash persistido; si el usuario ya existe,
 *       la posesión del teléfono probada por el OTP es prueba suficiente de identidad y se
 *       emite un custom token real (login multi-dispositivo) igual que en {@code register}</li>
 *   <li>register → crea la identidad real en Firebase Authentication, completa el perfil
 *       en Firestore, crea cuenta demo, y emite un custom token real</li>
 *   <li>pin/setup → configura PIN</li>
 *   <li>pin/verify → valida PIN</li>
 *   <li>me → perfil completo</li>
 *   <li>logout → cierra sesión</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long OTP_LIFETIME_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final long PIN_LOCKOUT_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final PhoneOtpRepository phoneOtpRepository;
    private final OtpSender otpSender;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ConfirmationTokenService confirmationTokenService;
    private final DeviceService deviceService;

    public AuthService(FirebaseAuth firebaseAuth,
                       UserRepository userRepository,
                       AccountRepository accountRepository,
                       AuditLogRepository auditLogRepository,
                       PhoneOtpRepository phoneOtpRepository,
                       OtpSender otpSender,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       ConfirmationTokenService confirmationTokenService,
                       DeviceService deviceService) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.phoneOtpRepository = phoneOtpRepository;
        this.otpSender = otpSender;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.confirmationTokenService = confirmationTokenService;
        this.deviceService = deviceService;
    }

    public AuthResponse startPhoneVerification(String phone) {
        log.info("Starting phone verification for: {}", phone);
        String code = generateOtpCode();
        Instant now = Instant.now();

        phoneOtpRepository.save(PhoneOtp.builder()
                .phone(phone)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plus(OTP_LIFETIME_MINUTES, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .createdAt(now)
                .build());

        otpSender.send(phone, code);

        return AuthResponse.builder()
                .message("Verification code sent to " + phone)
                .requiresRegistration(false)
                .requiresPin(false)
                .authenticated(false)
                .build();
    }

    public AuthResponse verifyPhone(String phone, String code) {
        log.info("Verifying phone: {}", phone);

        PhoneOtp otp = phoneOtpRepository.findByPhone(phone)
                .orElseThrow(() -> new InvalidOtpException("No verification code was requested for this phone"));

        if (otp.isUsed()) {
            throw new InvalidOtpException("Verification code has already been used");
        }
        if (otp.getExpiresAt() == null || otp.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException("Verification code has expired");
        }
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new InvalidOtpException("Too many failed attempts. Request a new verification code");
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            phoneOtpRepository.registerFailedAttempt(phone, otp.getAttempts() + 1);
            throw new InvalidOtpException("Invalid verification code");
        }

        phoneOtpRepository.markUsed(phone);

        Optional<User> existingUser = userRepository.findByPhone(phone);
        boolean exists = existingUser.isPresent();
        boolean requiresRegistration = !exists;
        boolean requiresPin = exists && existingUser
                .map(u -> u.getPin() == null || u.getPin().isEmpty())
                .orElse(false);

        // Verificar el OTP es, para un usuario ya existente, la prueba de posesión del
        // teléfono — exactamente la misma prueba que `register()` acepta para emitir un
        // custom token a un usuario nuevo. Sin esto, un usuario ya registrado no tenía forma
        // de autenticarse desde un dispositivo sin sesión Firebase persistida (pin/verify
        // exige Bearer, pero nunca se emitía un token fuera de register()). El PIN sigue
        // siendo el gate real para desbloquear la sesión/operar — este token solo permite
        // llegar hasta pin/verify o pin/setup.
        String token = exists ? createCustomToken(existingUser.get().getId()) : null;

        return AuthResponse.builder()
                .message(requiresRegistration ? "Phone verified. Please register." : "Phone verified.")
                .requiresRegistration(requiresRegistration)
                .requiresPin(requiresPin)
                .authenticated(exists)
                .token(token)
                .build();
    }

    public boolean userExistsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateUserException("A user with this phone number is already registered");
        }

        String userId = "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String displayName = request.getFirstName() + " " + request.getLastName();

        createFirebaseIdentity(userId, request.getPhone(), request.getEmail(), displayName);

        User user = User.builder()
                .id(userId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .documentId(request.getDocumentId())
                .address(request.getAddress())
                .country(request.getCountry())
                .email(request.getEmail())
                .displayName(displayName)
                .phone(request.getPhone())
                .status("ACTIVE")
                .roles(List.of("USER"))
                .pin(passwordEncoder.encode(request.getPin()))
                .biometricEnabled(false)
                .faceTemplate(null)
                .faceVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastLoginAt(Instant.now())
                .build();
        userRepository.save(user);

        // Crear cuenta de demo
        String accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Account account = Account.builder()
                .id(accountId)
                .userId(userId)
                .name("Cuenta de Ahorros Principal")
                .type("SAVINGS")
                .currency("COP")
                .balance(BigDecimal.valueOf(1000000.00))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        accountRepository.save(account);

        // Auditoría
        // Map.of(...) lanza NullPointerException si algún valor es null — y
        // "email" es opcional en RegisterRequest (sin @NotBlank), así que un
        // registro sin email crasheaba este audit log con un 500 genérico.
        Map<String, Object> registerAuditMetadata = new HashMap<>();
        registerAuditMetadata.put("phone", request.getPhone());
        registerAuditMetadata.put("email", request.getEmail());
        auditLogRepository.save(com.nexabank.api.model.AuditLog.builder()
                .userId(userId)
                .action("USER_REGISTER")
                .resourceType("user")
                .resourceId(userId)
                .status("COMPLETED")
                .timestamp(Instant.now())
                .metadata(registerAuditMetadata)
                .build());

        String token = createCustomToken(userId);

        return AuthResponse.builder()
                .message("User registered successfully")
                .requiresRegistration(false)
                .requiresPin(false)
                .authenticated(true)
                .token(token)
                .user(userMapper.toResponse(user))
                .build();
    }

    private void createFirebaseIdentity(String userId, String phone, String email, String displayName) {
        try {
            UserRecord.CreateRequest firebaseUserRequest = new UserRecord.CreateRequest()
                    .setUid(userId)
                    .setPhoneNumber(phone)
                    .setDisplayName(displayName);
            if (email != null && !email.isBlank()) {
                firebaseUserRequest.setEmail(email);
            }
            firebaseAuth.createUser(firebaseUserRequest);
            firebaseAuth.setCustomUserClaims(userId, Map.of("roles", List.of("USER")));
        } catch (FirebaseAuthException e) {
            log.error("Error creating Firebase Authentication identity for {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to create authentication identity", e);
        }
    }

    private String createCustomToken(String userId) {
        try {
            return firebaseAuth.createCustomToken(userId);
        } catch (FirebaseAuthException e) {
            log.error("Error issuing custom token for {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to issue authentication token", e);
        }
    }

    private String generateOtpCode() {
        int code = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    public AuthResponse setupPin(String uid, String pin) {
        return setupPin(uid, pin, null, null, null);
    }

    public AuthResponse setupPin(String uid, String pin, String deviceId, String deviceName, String platform) {
        log.info("Setting PIN for user: {}", uid);
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPin(passwordEncoder.encode(pin));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        deviceService.recordSeen(uid, deviceId, deviceName, platform);

        return AuthResponse.builder()
                .message("PIN configured successfully")
                .authenticated(true)
                .build();
    }

    /**
     * Verifica el PIN del usuario.
     *
     * <p>Migración oportunista (mejora #14): el PIN se hashea con bcrypt en {@code setupPin}/
     * {@code register}, pero los documentos de usuario sembrados antes de este cambio tienen el
     * PIN en texto plano. Estrategia:
     * <ol>
     *   <li>Si el valor almacenado parece un hash bcrypt (empieza con {@code $2}), se verifica
     *       con {@code PasswordEncoder.matches}.</li>
     *   <li>Si no, se compara en texto plano por compatibilidad retroactiva; si coincide, se
     *       re-hashea inmediatamente y se persiste antes de devolver éxito, completando la
     *       migración de ese usuario para la próxima verificación.</li>
     * </ol>
     */
    public AuthResponse verifyPin(String uid, String pin) {
        return verifyPin(uid, pin, null, null, null);
    }

    public AuthResponse verifyPin(String uid, String pin, String deviceId, String deviceName, String platform) {
        log.info("Verifying PIN for user: {}", uid);
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(Instant.now())) {
            throw new PinLockedException(
                    "Too many incorrect PIN attempts. Account locked until " + user.getPinLockedUntil());
        }

        String storedPin = user.getPin();
        boolean valid;
        boolean needsRehash = false;

        if (isBcryptHash(storedPin)) {
            valid = passwordEncoder.matches(pin, storedPin);
        } else {
            valid = storedPin != null && storedPin.equals(pin);
            needsRehash = valid;
        }

        if (valid) {
            boolean needsReset = user.getPinFailedAttempts() > 0 || user.getPinLockedUntil() != null;
            if (needsRehash) {
                log.info("Rehashing legacy plaintext PIN for user: {}", uid);
                user.setPin(passwordEncoder.encode(pin));
            }
            if (needsRehash || needsReset) {
                user.setPinFailedAttempts(0);
                user.setPinLockedUntil(null);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            }
            deviceService.recordSeen(uid, deviceId, deviceName, platform);
        } else {
            registerFailedPinAttempt(user);
        }

        String confirmationToken = valid ? confirmationTokenService.generate(uid) : null;

        return AuthResponse.builder()
                .message(valid ? "PIN verified" : "Invalid PIN")
                .authenticated(valid)
                .confirmationToken(confirmationToken)
                .build();
    }

    /**
     * Bloqueo temporal tras {@link #MAX_PIN_ATTEMPTS} fallos consecutivos (mejora de
     * seguridad pre-producción) — mismo criterio que ya aplicaba {@code PhoneOtp.attempts}
     * para el OTP, pero sobre el propio documento de usuario.
     */
    private void registerFailedPinAttempt(User user) {
        int attempts = user.getPinFailedAttempts() + 1;
        user.setPinFailedAttempts(attempts);
        if (attempts >= MAX_PIN_ATTEMPTS) {
            user.setPinLockedUntil(Instant.now().plus(PIN_LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            user.setPinFailedAttempts(0);
            log.warn("PIN locked for user {} after {} failed attempts", user.getId(), MAX_PIN_ATTEMPTS);
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private boolean isBcryptHash(String value) {
        return value != null && value.startsWith("$2");
    }

    public AuthResponse getCurrentUser(String uid) {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserResponse response = userMapper.toResponse(user);
        return AuthResponse.builder()
                .message("User profile retrieved")
                .authenticated(true)
                .user(response)
                .build();
    }

    public AuthResponse logout() {
        return AuthResponse.builder()
                .message("Logged out successfully")
                .authenticated(false)
                .build();
    }

    public String extractUidFromToken(String token) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(token);
            return decoded.getUid();
        } catch (Exception e) {
            log.warn("Invalid Firebase token: {}", e.getMessage());
            throw new AuthenticationRequiredException("Invalid or expired Firebase ID token.");
        }
    }
}
