package com.nexabank.api.controller;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.dto.fx.ExchangeRateResponse;
import com.nexabank.api.dto.fx.QuoteRequest;
import com.nexabank.api.dto.fx.QuoteResponse;
import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.service.FeeService;
import com.nexabank.api.service.FxService;
import com.nexabank.api.service.TransferService;
import com.nexabank.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Controlador de divisas (FX) para transferencias internacionales.
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/fx/rates/{base}/{target} — tipo de cambio de un par</li>
 *   <li>POST /api/v1/fx/convert — convierte un monto entre divisas</li>
 *   <li>POST /api/v1/fx/quote — cotización previa de una transferencia</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fx")
public class FxController {

    private final FxService fxService;
    private final FeeService feeService;
    private final TransferService transferService;
    private final SecurityUtils securityUtils;

    public FxController(FxService fxService, FeeService feeService,
                        TransferService transferService, SecurityUtils securityUtils) {
        this.fxService = fxService;
        this.feeService = feeService;
        this.transferService = transferService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/rates/{base}/{target}")
    public ResponseEntity<ExchangeRateResponse> getRate(
            @PathVariable("base") String base, @PathVariable("target") String target) {
        return ResponseEntity.ok(fxService.getRate(base, target));
    }

    @PostMapping("/convert")
    public ResponseEntity<ConvertCurrencyResponse> convert(
            @Valid @RequestBody ConvertCurrencyRequest request) {
        return ResponseEntity.ok(fxService.convert(request));
    }

    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(
            @Valid @RequestBody QuoteRequest request) {
        String userId = securityUtils.getCurrentUserId();
        QuoteResponse quote = transferService.quoteTransfer(userId,
                toCreateTransferRequest(request));
        return ResponseEntity.ok(quote);
    }

    private CreateTransferRequest toCreateTransferRequest(QuoteRequest request) {
        return CreateTransferRequest.builder()
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .sourceCurrency(request.getSourceCurrency())
                .destinationCurrency(request.getDestinationCurrency())
                .currency(request.getSourceCurrency())
                .build();
    }
}
