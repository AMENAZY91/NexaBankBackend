package com.nexabank.api.service;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.exception.InvalidCurrencyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Servicio de cálculo de comisiones para transferencias.
 *
 * <p>Soporta comisiones fijas, porcentuales y márgenes de tipo de cambio
 * (FX margin) para transferencias internacionales.
 */
@Service
public class FeeService {

    private static final BigDecimal FLAT_FEE = new BigDecimal("2.50");
    private static final BigDecimal PERCENT_FEE_RATE = new BigDecimal("0.01"); // 1%
    private static final BigDecimal FX_MARGIN_RATE = new BigDecimal("0.005");  // 0.5%
    private static final BigDecimal MIN_FEE = new BigDecimal("1.00");

    public BigDecimal calculateTransferFee(BigDecimal amount, String sourceCurrency,
                                           String destinationCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidCurrencyException("Amount must be positive");
        }

        BigDecimal flat = FLAT_FEE;
        BigDecimal percent = amount.multiply(PERCENT_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = flat.add(percent);

        // Recargo adicional cuando hay conversión de moneda (cambio cruzado).
        if (!sourceCurrency.equalsIgnoreCase(destinationCurrency)) {
            fee = fee.add(amount.multiply(FX_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP));
        }

        return fee.max(MIN_FEE);
    }

    public BigDecimal calculateFxMargin(BigDecimal amount, BigDecimal baseRate) {
        if (amount == null || baseRate == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(baseRate).multiply(FX_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public ConvertCurrencyResponse applyQuote(ConvertCurrencyRequest request,
                                              ConvertCurrencyResponse conversion) {
        BigDecimal fee = calculateTransferFee(request.getAmount(),
                request.getSourceCurrency(), request.getTargetCurrency());
        BigDecimal total = request.getAmount().add(fee);
        return ConvertCurrencyResponse.builder()
                .sourceCurrency(conversion.getSourceCurrency())
                .targetCurrency(conversion.getTargetCurrency())
                .sourceAmount(conversion.getSourceAmount())
                .targetAmount(conversion.getTargetAmount())
                .rate(conversion.getRate())
                .fee(fee)
                .total(total)
                .build();
    }
}
