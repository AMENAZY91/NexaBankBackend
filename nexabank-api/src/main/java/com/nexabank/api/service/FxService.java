package com.nexabank.api.service;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.dto.fx.ExchangeRateResponse;
import com.nexabank.api.exception.UnsupportedCurrencyException;
import com.nexabank.api.model.ExchangeRate;
import com.nexabank.api.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de tipo de cambio (FX) para transferencias internacionales.
 *
 * <p>Proporciona tasas de cambio y conversión de divisas. Utiliza tasas
 * persistidas en Firestore con un mapa de respaldo en memoria para divisas
 * comunes cuando no hay una tasa disponible.
 */
@Service
public class FxService {

    private static final Logger log = LoggerFactory.getLogger(FxService.class);

    private static final Map<String, BigDecimal> FALLBACK_RATES = new ConcurrentHashMap<>();

    static {
        // Tasas de referencia aproximadas (1 unidad de base → unidades de target).
        // Se sustituyen por una fuente real en producción.
        FALLBACK_RATES.put("USD_COP", new BigDecimal("4000.00"));
        FALLBACK_RATES.put("COP_USD", new BigDecimal("0.00025"));
        FALLBACK_RATES.put("USD_EUR", new BigDecimal("0.92"));
        FALLBACK_RATES.put("EUR_USD", new BigDecimal("1.09"));
        FALLBACK_RATES.put("USD_MXN", new BigDecimal("17.50"));
        FALLBACK_RATES.put("MXN_USD", new BigDecimal("0.057"));
        FALLBACK_RATES.put("EUR_COP", new BigDecimal("4350.00"));
        FALLBACK_RATES.put("COP_EUR", new BigDecimal("0.00023"));
        FALLBACK_RATES.put("USD_GBP", new BigDecimal("0.79"));
        FALLBACK_RATES.put("GBP_USD", new BigDecimal("1.27"));
        FALLBACK_RATES.put("USD_BRL", new BigDecimal("5.10"));
        FALLBACK_RATES.put("BRL_USD", new BigDecimal("0.196"));
    }

    private final ExchangeRateRepository exchangeRateRepository;

    public FxService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public ExchangeRateResponse getRate(String baseCurrency, String targetCurrency) {
        String base = baseCurrency.toUpperCase();
        String target = targetCurrency.toUpperCase();
        validateCurrency(base);
        validateCurrency(target);

        Optional<ExchangeRate> persisted = exchangeRateRepository.findLatestByPair(base, target);
        if (persisted.isPresent() && !isExpired(persisted.get())) {
            ExchangeRate rate = persisted.get();
            return toResponse(rate);
        }

        BigDecimal rateValue = FALLBACK_RATES.get(base + "_" + target);
        if (rateValue == null) {
            throw new UnsupportedCurrencyException(
                    "No exchange rate available for " + base + " → " + target);
        }

        ExchangeRate fallback = ExchangeRate.builder()
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(rateValue)
                .timestamp(Instant.now())
                .source("fallback")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        exchangeRateRepository.save(fallback);
        return toResponse(fallback);
    }

    public ConvertCurrencyResponse convert(ConvertCurrencyRequest request) {
        String source = request.getSourceCurrency().toUpperCase();
        String target = request.getTargetCurrency().toUpperCase();
        validateCurrency(source);
        validateCurrency(target);

        BigDecimal rate;
        if (source.equals(target)) {
            rate = BigDecimal.ONE;
        } else {
            ExchangeRateResponse fx = getRate(source, target);
            rate = fx.getRate();
        }

        BigDecimal sourceAmount = request.getAmount();
        BigDecimal targetAmount = sourceAmount.multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = calculateFee(sourceAmount, source, target);
        BigDecimal total = sourceAmount.add(fee);

        return ConvertCurrencyResponse.builder()
                .sourceCurrency(source)
                .targetCurrency(target)
                .sourceAmount(sourceAmount)
                .targetAmount(targetAmount)
                .rate(rate)
                .fee(fee)
                .total(total)
                .build();
    }

    private boolean isExpired(ExchangeRate rate) {
        return rate.getExpiresAt() != null && rate.getExpiresAt().isBefore(Instant.now());
    }

    private ExchangeRateResponse toResponse(ExchangeRate rate) {
        return ExchangeRateResponse.builder()
                .baseCurrency(rate.getBaseCurrency())
                .targetCurrency(rate.getTargetCurrency())
                .rate(rate.getRate())
                .timestamp(rate.getTimestamp())
                .source(rate.getSource())
                .expiresAt(rate.getExpiresAt())
                .build();
    }

    private void validateCurrency(String currency) {
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
        }
    }

    BigDecimal calculateFee(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        // Fee fijo mínimo + porcentaje sobre el monto para transferencias internacionales.
        BigDecimal flatFee = BigDecimal.valueOf(2.50);
        BigDecimal percentFee = amount.multiply(BigDecimal.valueOf(0.01)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = flatFee.add(percentFee);
        return fee.max(BigDecimal.valueOf(1.00));
    }
}
