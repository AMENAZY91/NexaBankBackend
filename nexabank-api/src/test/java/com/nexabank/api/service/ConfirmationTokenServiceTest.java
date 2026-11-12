package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.model.ConfirmationToken;
import com.nexabank.api.repository.ConfirmationTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmationTokenServiceTest {

    @Mock
    private ConfirmationTokenRepository confirmationTokenRepository;

    private ConfirmationTokenService confirmationTokenService;

    @BeforeEach
    void setUp() {
        confirmationTokenService = new ConfirmationTokenService(confirmationTokenRepository);
    }

    @Test
    void generate_shouldPersistTokenWithFiveMinuteExpiry() {
        String token = confirmationTokenService.generate("USR-000001");

        ArgumentCaptor<ConfirmationToken> captor = ArgumentCaptor.forClass(ConfirmationToken.class);
        verify(confirmationTokenRepository).save(captor.capture());

        ConfirmationToken saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(token);
        assertThat(saved.getUserId()).isEqualTo("USR-000001");
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getExpiresAt())
                .isAfter(Instant.now().plus(4, ChronoUnit.MINUTES))
                .isBefore(Instant.now().plus(6, ChronoUnit.MINUTES));
    }

    @Test
    void validateAndConsume_shouldSucceed_whenTokenValid() {
        ConfirmationToken token = ConfirmationToken.builder()
                .token("tok-1").userId("USR-000001")
                .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .used(false).build();
        when(confirmationTokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        confirmationTokenService.validateAndConsume("tok-1", "USR-000001");

        verify(confirmationTokenRepository).markUsed("tok-1");
    }

    @Test
    void validateAndConsume_shouldThrow_whenTokenMissing() {
        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume(null, "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume("", "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }

    @Test
    void validateAndConsume_shouldThrow_whenTokenNotFound() {
        when(confirmationTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume("missing", "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }

    @Test
    void validateAndConsume_shouldThrow_whenTokenBelongsToDifferentUser() {
        ConfirmationToken token = ConfirmationToken.builder()
                .token("tok-1").userId("USR-999999")
                .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .used(false).build();
        when(confirmationTokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume("tok-1", "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }

    @Test
    void validateAndConsume_shouldThrow_whenTokenExpired() {
        ConfirmationToken token = ConfirmationToken.builder()
                .token("tok-1").userId("USR-000001")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .used(false).build();
        when(confirmationTokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume("tok-1", "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }

    @Test
    void validateAndConsume_shouldThrow_whenTokenAlreadyUsed() {
        ConfirmationToken token = ConfirmationToken.builder()
                .token("tok-1").userId("USR-000001")
                .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .used(true).build();
        when(confirmationTokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> confirmationTokenService.validateAndConsume("tok-1", "USR-000001"))
                .isInstanceOf(InvalidConfirmationTokenException.class);
    }
}
