package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.nexabank.api.dto.auth.AuthResponse;
import com.nexabank.api.dto.user.RegisterRequest;
import com.nexabank.api.exception.DuplicateUserException;
import com.nexabank.api.exception.InvalidOtpException;
import com.nexabank.api.exception.PinLockedException;
import com.nexabank.api.mapper.UserMapper;
import com.nexabank.api.model.PhoneOtp;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.PhoneOtpRepository;
import com.nexabank.api.repository.UserRepository;
import com.nexabank.api.service.otp.OtpSender;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FirebaseAuth firebaseAuth;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private PhoneOtpRepository phoneOtpRepository;
    @Mock
    private OtpSender otpSender;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ConfirmationTokenService confirmationTokenService;
    @Mock
    private DeviceService deviceService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Encoder real (no mock): queremos probar hashing/verificación de PIN de verdad.
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(firebaseAuth, userRepository, accountRepository,
                auditLogRepository, phoneOtpRepository, otpSender, userMapper, passwordEncoder,
                confirmationTokenService, deviceService);
    }

    @Test
    void setupPin_shouldStoreBcryptHash_notPlaintext() {
        User user = User.builder().id("USR-000001").pin(null).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        authService.setupPin("USR-000001", "1234");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedPin = captor.getValue().getPin();

        assertThat(storedPin).isNotEqualTo("1234");
        assertThat(storedPin).startsWith("$2");
        assertThat(passwordEncoder.matches("1234", storedPin)).isTrue();
    }

    @Test
    void verifyPin_shouldSucceed_againstBcryptHash_andIssueConfirmationToken() {
        String hash = passwordEncoder.encode("1234");
        User user = User.builder().id("USR-000001").pin(hash).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(confirmationTokenService.generate("USR-000001")).thenReturn("confirm-token-xyz");

        AuthResponse response = authService.verifyPin("USR-000001", "1234");

        assertThat(response.getAuthenticated()).isTrue();
        assertThat(response.getConfirmationToken()).isEqualTo("confirm-token-xyz");
        // El hash ya era bcrypt: no debe reescribirse.
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyPin_shouldSucceed_againstLegacyPlaintextPin_andTriggerRehash() {
        User user = User.builder().id("USR-000001").pin("1234").build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(confirmationTokenService.generate("USR-000001")).thenReturn("confirm-token-xyz");

        AuthResponse response = authService.verifyPin("USR-000001", "1234");

        assertThat(response.getAuthenticated()).isTrue();
        assertThat(response.getConfirmationToken()).isEqualTo("confirm-token-xyz");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String rehashed = captor.getValue().getPin();
        assertThat(rehashed).startsWith("$2");
        assertThat(passwordEncoder.matches("1234", rehashed)).isTrue();
    }

    @Test
    void verifyPin_shouldFail_whenPinIncorrect_andNotIssueConfirmationToken() {
        String hash = passwordEncoder.encode("1234");
        User user = User.builder().id("USR-000001").pin(hash).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        AuthResponse response = authService.verifyPin("USR-000001", "9999");

        assertThat(response.getAuthenticated()).isFalse();
        assertThat(response.getConfirmationToken()).isNull();
        verify(confirmationTokenService, never()).generate(any());
    }

    @Test
    void verifyPin_shouldRecordDevice_whenDeviceIdProvidedAndPinValid() {
        String hash = passwordEncoder.encode("1234");
        User user = User.builder().id("USR-000001").pin(hash).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(confirmationTokenService.generate("USR-000001")).thenReturn("confirm-token-xyz");

        authService.verifyPin("USR-000001", "1234", "device-abc", "iPhone 15", "ios");

        verify(deviceService).recordSeen("USR-000001", "device-abc", "iPhone 15", "ios");
    }

    @Test
    void verifyPin_shouldNotRecordDevice_whenPinIncorrect() {
        String hash = passwordEncoder.encode("1234");
        User user = User.builder().id("USR-000001").pin(hash).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        authService.verifyPin("USR-000001", "9999", "device-abc", "iPhone 15", "ios");

        verify(deviceService, never()).recordSeen(any(), any(), any(), any());
    }

    @Test
    void verifyPin_shouldThrowPinLocked_whenAccountIsLocked() {
        User user = User.builder().id("USR-000001").pin(passwordEncoder.encode("1234"))
                .pinLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES)).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyPin("USR-000001", "1234"))
                .isInstanceOf(PinLockedException.class);
        verify(confirmationTokenService, never()).generate(any());
    }

    @Test
    void verifyPin_shouldSucceed_whenLockExpired() {
        User user = User.builder().id("USR-000001").pin(passwordEncoder.encode("1234"))
                .pinLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES)).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(confirmationTokenService.generate("USR-000001")).thenReturn("confirm-token-xyz");

        AuthResponse response = authService.verifyPin("USR-000001", "1234");

        assertThat(response.getAuthenticated()).isTrue();
    }

    @Test
    void verifyPin_shouldIncrementFailedAttempts_belowThreshold_withoutLocking() {
        User user = User.builder().id("USR-000001").pin(passwordEncoder.encode("1234"))
                .pinFailedAttempts(2).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        authService.verifyPin("USR-000001", "9999");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinFailedAttempts()).isEqualTo(3);
        assertThat(captor.getValue().getPinLockedUntil()).isNull();
    }

    @Test
    void verifyPin_shouldLockAccount_onFifthConsecutiveFailure() {
        User user = User.builder().id("USR-000001").pin(passwordEncoder.encode("1234"))
                .pinFailedAttempts(4).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        authService.verifyPin("USR-000001", "9999");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinFailedAttempts()).isZero();
        assertThat(captor.getValue().getPinLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void verifyPin_shouldResetFailedAttempts_afterSuccessFollowingPriorFailures() {
        User user = User.builder().id("USR-000001").pin(passwordEncoder.encode("1234"))
                .pinFailedAttempts(3).build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(confirmationTokenService.generate("USR-000001")).thenReturn("confirm-token-xyz");

        AuthResponse response = authService.verifyPin("USR-000001", "1234");

        assertThat(response.getAuthenticated()).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinFailedAttempts()).isZero();
    }

    @Test
    void verifyPin_shouldFail_whenLegacyPlaintextPinIncorrect_andNotRehash() {
        User user = User.builder().id("USR-000001").pin("1234").build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));

        AuthResponse response = authService.verifyPin("USR-000001", "9999");

        assertThat(response.getAuthenticated()).isFalse();
        // Sí se guarda (para persistir el contador de intentos fallidos del lockout), pero
        // el PIN legacy en texto plano permanece sin re-hashear — el rehash solo ocurre
        // cuando el PIN ingresado es correcto.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPin()).isEqualTo("1234");
        assertThat(captor.getValue().getPinFailedAttempts()).isEqualTo(1);
    }

    @Test
    void startPhoneVerification_shouldPersistHashedOtp_andSendPlaintextViaOtpSender() {
        authService.startPhoneVerification("+573000000000");

        ArgumentCaptor<PhoneOtp> otpCaptor = ArgumentCaptor.forClass(PhoneOtp.class);
        verify(phoneOtpRepository).save(otpCaptor.capture());
        PhoneOtp saved = otpCaptor.getValue();
        assertThat(saved.getPhone()).isEqualTo("+573000000000");
        assertThat(saved.getCodeHash()).startsWith("$2");
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getAttempts()).isZero();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpSender).send(eq("+573000000000"), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(passwordEncoder.matches(codeCaptor.getValue(), saved.getCodeHash())).isTrue();
    }

    @Test
    void verifyPhone_shouldSucceed_withCorrectCode_andMarkOtpUsed() throws FirebaseAuthException {
        String code = "123456";
        PhoneOtp otp = PhoneOtp.builder()
                .phone("+573000000000")
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .build();
        when(phoneOtpRepository.findByPhone("+573000000000")).thenReturn(Optional.of(otp));
        when(userRepository.findByPhone("+573000000000")).thenReturn(Optional.empty());

        AuthResponse response = authService.verifyPhone("+573000000000", code);

        assertThat(response.getRequiresRegistration()).isTrue();
        assertThat(response.getAuthenticated()).isFalse();
        assertThat(response.getToken()).isNull();
        verify(phoneOtpRepository).markUsed("+573000000000");
        verify(phoneOtpRepository, never()).registerFailedAttempt(anyString(), any(Integer.class));
        verify(firebaseAuth, never()).createCustomToken(any());
    }

    @Test
    void verifyPhone_shouldIssueRealCustomToken_whenUserAlreadyExists() throws FirebaseAuthException {
        String code = "123456";
        PhoneOtp otp = PhoneOtp.builder()
                .phone("+573000000000")
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .build();
        User existingUser = User.builder().id("USR-000001").phone("+573000000000")
                .pin(passwordEncoder.encode("1234")).build();
        when(phoneOtpRepository.findByPhone("+573000000000")).thenReturn(Optional.of(otp));
        when(userRepository.findByPhone("+573000000000")).thenReturn(Optional.of(existingUser));
        when(firebaseAuth.createCustomToken("USR-000001")).thenReturn("real-firebase-custom-token");

        AuthResponse response = authService.verifyPhone("+573000000000", code);

        assertThat(response.getRequiresRegistration()).isFalse();
        assertThat(response.getRequiresPin()).isFalse();
        assertThat(response.getAuthenticated()).isTrue();
        assertThat(response.getToken()).isEqualTo("real-firebase-custom-token");
    }

    @Test
    void verifyPhone_shouldRequirePin_whenExistingUserHasNoPinSetYet() throws FirebaseAuthException {
        String code = "123456";
        PhoneOtp otp = PhoneOtp.builder()
                .phone("+573000000000")
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .build();
        User existingUser = User.builder().id("USR-000001").phone("+573000000000").pin(null).build();
        when(phoneOtpRepository.findByPhone("+573000000000")).thenReturn(Optional.of(otp));
        when(userRepository.findByPhone("+573000000000")).thenReturn(Optional.of(existingUser));
        when(firebaseAuth.createCustomToken("USR-000001")).thenReturn("real-firebase-custom-token");

        AuthResponse response = authService.verifyPhone("+573000000000", code);

        assertThat(response.getRequiresRegistration()).isFalse();
        assertThat(response.getRequiresPin()).isTrue();
        assertThat(response.getToken()).isEqualTo("real-firebase-custom-token");
    }

    @Test
    void verifyPhone_shouldThrow_whenCodeIncorrect_andRegisterFailedAttempt() {
        PhoneOtp otp = PhoneOtp.builder()
                .phone("+573000000000")
                .codeHash(passwordEncoder.encode("123456"))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(1)
                .used(false)
                .build();
        when(phoneOtpRepository.findByPhone("+573000000000")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyPhone("+573000000000", "000000"))
                .isInstanceOf(InvalidOtpException.class);

        verify(phoneOtpRepository).registerFailedAttempt("+573000000000", 2);
        verify(phoneOtpRepository, never()).markUsed(any());
    }

    @Test
    void verifyPhone_shouldThrow_whenOtpExpired() {
        PhoneOtp otp = PhoneOtp.builder()
                .phone("+573000000000")
                .codeHash(passwordEncoder.encode("123456"))
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .attempts(0)
                .used(false)
                .build();
        when(phoneOtpRepository.findByPhone("+573000000000")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.verifyPhone("+573000000000", "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void register_shouldThrow_whenPhoneAlreadyRegistered() throws FirebaseAuthException {
        when(userRepository.existsByPhone("+573000000000")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder()
                .phone("+573000000000")
                .firstName("Juan")
                .lastName("Perez")
                .documentId("12345678")
                .address("Calle 123")
                .country("CO")
                .email("juan@example.com")
                .displayName("Juan Perez")
                .pin("1234")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUserException.class);

        verify(firebaseAuth, never()).createUser(any());
    }

    @Test
    void register_shouldCreateFirebaseIdentity_setRoleClaims_andReturnRealCustomToken() throws FirebaseAuthException {
        when(userRepository.existsByPhone("+573000000000")).thenReturn(false);
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(mock(UserRecord.class));
        when(firebaseAuth.createCustomToken(anyString())).thenReturn("real-firebase-custom-token");

        RegisterRequest request = RegisterRequest.builder()
                .phone("+573000000000")
                .firstName("Juan")
                .lastName("Perez")
                .documentId("12345678")
                .address("Calle 123")
                .country("CO")
                .email("juan@example.com")
                .displayName("Juan Perez")
                .pin("1234")
                .build();

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("real-firebase-custom-token");
        assertThat(response.getAuthenticated()).isTrue();

        ArgumentCaptor<UserRecord.CreateRequest> createCaptor = ArgumentCaptor.forClass(UserRecord.CreateRequest.class);
        verify(firebaseAuth).createUser(createCaptor.capture());

        verify(firebaseAuth).setCustomUserClaims(anyString(), eq(java.util.Map.of("roles", java.util.List.of("USER"))));
        verify(userRepository).save(any(User.class));
        verify(accountRepository).save(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    void getCurrentUser_shouldNotReturnAToken() {
        User user = User.builder().id("USR-000001").firstName("Juan").build();
        when(userRepository.findById("USR-000001")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(com.nexabank.api.dto.user.UserResponse.builder().id("USR-000001").build());

        AuthResponse response = authService.getCurrentUser("USR-000001");

        assertThat(response.getToken()).isNull();
        assertThat(response.getUser()).isNotNull();
    }
}
