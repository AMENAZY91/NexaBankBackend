package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.exception.InvalidCurrencyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeeServiceTest {

    private final FeeService feeService = new FeeService();

    @Test
    void calculateTransferFee_shouldApplyFlatPlusPercent() {
        BigDecimal fee = feeService.calculateTransferFee(
                new BigDecimal("1000.00"), "USD", "USD");

        // 2.50 flat + 1% (10.00) = 12.50
        assertThat(fee).isEqualByComparingTo("12.50");
    }

    @Test
    void calculateTransferFee_shouldAddFxMargin_whenCrossCurrency() {
        BigDecimal same = feeService.calculateTransferFee(
                new BigDecimal("1000.00"), "USD", "USD");
        BigDecimal cross = feeService.calculateTransferFee(
                new BigDecimal("1000.00"), "USD", "COP");

        assertThat(cross).isGreaterThan(same);
    }

    @Test
    void calculateTransferFee_shouldReturnMinFee_whenAmountIsTiny() {
        BigDecimal fee = feeService.calculateTransferFee(
                new BigDecimal("0.01"), "USD", "USD");

        // flat 2.50 + ~0 = 2.50, max(2.50, 1.00) = 2.50
        assertThat(fee).isEqualByComparingTo("2.50");
    }

    @Test
    void calculateTransferFee_shouldThrow_whenAmountNotPositive() {
        assertThatThrownBy(() -> feeService.calculateTransferFee(
                new BigDecimal("-100.00"), "USD", "USD"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void applyQuote_shouldIncludeFeeAndTotal() {
        ConvertCurrencyRequest request = ConvertCurrencyRequest.builder()
                .sourceCurrency("USD").targetCurrency("COP")
                .amount(new BigDecimal("100.00")).build();
        ConvertCurrencyResponse conversion = ConvertCurrencyResponse.builder()
                .sourceCurrency("USD").targetCurrency("COP")
                .sourceAmount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("400000.00"))
                .rate(new BigDecimal("4000.00"))
                .fee(new BigDecimal("0.00"))
                .total(new BigDecimal("100.00")).build();

        var response = feeService.applyQuote(request, conversion);

        // 2.50 flat + 1% (1.00) + FX margin 0.5% (0.50) = 4.00
        assertThat(response.getFee()).isEqualByComparingTo("4.00");
        assertThat(response.getTotal()).isEqualByComparingTo("104.00");
    }
}
