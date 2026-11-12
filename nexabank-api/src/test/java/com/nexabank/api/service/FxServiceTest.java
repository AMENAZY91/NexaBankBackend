package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.exception.UnsupportedCurrencyException;
import com.nexabank.api.model.ExchangeRate;
import com.nexabank.api.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private FxService fxService;

    @BeforeEach
    void setUp() {
        fxService = new FxService(exchangeRateRepository);
    }

    @Test
    void getRate_shouldReturnFallbackRate_whenNoPersistedRate() {
        when(exchangeRateRepository.findLatestByPair("USD", "COP"))
                .thenReturn(Optional.empty());

        var response = fxService.getRate("USD", "COP");

        assertThat(response.getBaseCurrency()).isEqualTo("USD");
        assertThat(response.getTargetCurrency()).isEqualTo("COP");
        assertThat(response.getRate()).isEqualByComparingTo("4000.00");
        assertThat(response.getSource()).isEqualTo("fallback");
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void getRate_shouldReturnPersistedRate_whenNotExpired() {
        ExchangeRate persisted = ExchangeRate.builder()
                .baseCurrency("USD").targetCurrency("COP")
                .rate(new BigDecimal("3900.00"))
                .timestamp(Instant.now())
                .source("provider")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(exchangeRateRepository.findLatestByPair("USD", "COP"))
                .thenReturn(Optional.of(persisted));

        var response = fxService.getRate("USD", "COP");

        assertThat(response.getRate()).isEqualByComparingTo("3900.00");
        assertThat(response.getSource()).isEqualTo("provider");
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void getRate_shouldThrowUnsupportedCurrency_whenNoRateAvailable() {
        when(exchangeRateRepository.findLatestByPair("USD", "XXX"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fxService.getRate("USD", "XXX"))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void convert_shouldReturnSameAmount_whenSameCurrency() {
        ConvertCurrencyRequest request = ConvertCurrencyRequest.builder()
                .sourceCurrency("USD").targetCurrency("USD")
                .amount(new BigDecimal("100.00")).build();

        var response = fxService.convert(request);

        assertThat(response.getRate()).isEqualByComparingTo("1.00");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void convert_shouldApplyRate_whenDifferentCurrencies() {
        when(exchangeRateRepository.findLatestByPair("USD", "COP"))
                .thenReturn(Optional.empty());

        ConvertCurrencyRequest request = ConvertCurrencyRequest.builder()
                .sourceCurrency("USD").targetCurrency("COP")
                .amount(new BigDecimal("100.00")).build();

        var response = fxService.convert(request);

        assertThat(response.getRate()).isEqualByComparingTo("4000.00");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("400000.00");
    }
}
