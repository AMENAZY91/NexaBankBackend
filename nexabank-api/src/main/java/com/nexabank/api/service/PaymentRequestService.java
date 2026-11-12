package com.nexabank.api.service;

import com.nexabank.api.dto.paymentrequest.CreatePaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PayPaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PaymentRequestResponse;
import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.PaymentRequestMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.PaymentRequest;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.PaymentRequestRepository;
import com.nexabank.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio de solicitudes de pago ("pídele dinero a alguien").
 *
 * <p>Compone sobre {@link TransferService} al pagarse ({@link #pay}) — no reimplementa
 * movimiento de dinero, reusa idempotencia/confirmation-token/FX/fees ya hardened ahí.
 *
 * <p>Modelo de autorización de dos capas: {@link #getAccessibleRequest} oculta la
 * existencia del recurso (404) a cualquiera que no sea el solicitante o el pagador
 * designado; cada acción (pagar/declinar/cancelar) aplica además su propia restricción
 * de rol (403) sobre ese subconjunto de "partes conocidas" — mismo criterio que
 * {@code TransferService.getTransferById}/{@code cancelTransfer}.
 */
@Service
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);

    private final PaymentRequestRepository paymentRequestRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransferService transferService;
    private final NotificationService notificationService;
    private final PaymentRequestMapper paymentRequestMapper;

    @Value("${app.payment-request.expiration-hours:48}")
    private long expirationHours;

    public PaymentRequestService(PaymentRequestRepository paymentRequestRepository,
                                  UserRepository userRepository,
                                  AccountRepository accountRepository,
                                  TransferService transferService,
                                  NotificationService notificationService,
                                  PaymentRequestMapper paymentRequestMapper) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transferService = transferService;
        this.notificationService = notificationService;
        this.paymentRequestMapper = paymentRequestMapper;
    }

    public PaymentRequestResponse create(CreatePaymentRequestRequest request, String requesterId) {
        User payer = userRepository.findByPhone(request.getPayerPhone())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No NexaBank user found with phone: " + request.getPayerPhone()));

        if (payer.getId().equals(requesterId)) {
            throw new InvalidTransactionException("You cannot request a payment from yourself");
        }

        Account requesterAccount = accountRepository.findById(request.getRequesterAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + request.getRequesterAccountId()));
        if (!requesterAccount.getUserId().equals(requesterId)) {
            throw new UnauthorizedOperationException("The account does not belong to the authenticated user");
        }

        Instant now = Instant.now();
        PaymentRequest pr = PaymentRequest.builder()
                .requesterId(requesterId)
                .requesterAccountId(requesterAccount.getId())
                .payerUserId(payer.getId())
                .payerPhone(request.getPayerPhone())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status("PENDING")
                .createdAt(now)
                .expiresAt(now.plus(expirationHours, ChronoUnit.HOURS))
                .build();
        PaymentRequest saved = paymentRequestRepository.save(pr);

        notificationService.notify(payer.getId(), "PAYMENT_REQUEST_RECEIVED", "Te solicitan un pago",
                "Te solicitaron " + request.getAmount() + " " + request.getCurrency(),
                "PAYMENT_REQUEST", saved.getId());

        log.info("Payment request {} created: {} {} requested by {} from {}",
                saved.getId(), request.getAmount(), request.getCurrency(), requesterId, payer.getId());

        return paymentRequestMapper.toResponse(saved);
    }

    public PaymentRequestResponse getById(String id, String userId) {
        return paymentRequestMapper.toResponse(getAccessibleRequest(id, userId));
    }

    public List<PaymentRequestResponse> getByUser(String userId, String role) {
        List<PaymentRequest> requests = "sent".equalsIgnoreCase(role)
                ? paymentRequestRepository.findByRequesterId(userId)
                : paymentRequestRepository.findByPayerUserId(userId);
        return requests.stream().map(paymentRequestMapper::toResponse).toList();
    }

    public PaymentRequestResponse pay(String id, PayPaymentRequestRequest payRequest,
                                       String idempotencyKeyHeader, String userId) {
        PaymentRequest pr = getAccessibleRequest(id, userId);

        if (!userId.equals(pr.getPayerUserId())) {
            throw new UnauthorizedOperationException("Only the designated payer can pay this request");
        }

        pr = failIfExpired(pr);
        if (!"PENDING".equalsIgnoreCase(pr.getStatus())) {
            throw new InvalidTransactionException("Only PENDING payment requests can be paid");
        }

        Account sourceAccount = accountRepository.findById(payRequest.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + payRequest.getSourceAccountId()));

        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(pr.getRequesterAccountId())
                .amount(pr.getAmount())
                .sourceCurrency(sourceAccount.getCurrency())
                .destinationCurrency(pr.getCurrency())
                .currency(sourceAccount.getCurrency())
                .description("Payment request " + pr.getId()
                        + (pr.getDescription() != null ? ": " + pr.getDescription() : ""))
                .build();

        TransferResponse transfer = transferService.executeTransfer(transferRequest, idempotencyKeyHeader, userId);

        PaymentRequest paid = pr.toBuilder().status("PAID").relatedTransferId(transfer.getId()).build();
        PaymentRequest saved = paymentRequestRepository.save(paid);

        log.info("Payment request {} paid via transfer {}", saved.getId(), transfer.getId());

        return paymentRequestMapper.toResponse(saved);
    }

    public PaymentRequestResponse decline(String id, String userId) {
        PaymentRequest pr = getAccessibleRequest(id, userId);

        if (!userId.equals(pr.getPayerUserId())) {
            throw new UnauthorizedOperationException("Only the designated payer can decline this request");
        }

        pr = failIfExpired(pr);
        if (!"PENDING".equalsIgnoreCase(pr.getStatus())) {
            throw new InvalidTransactionException("Only PENDING payment requests can be declined");
        }

        PaymentRequest declined = pr.toBuilder().status("DECLINED").build();
        PaymentRequest saved = paymentRequestRepository.save(declined);

        notificationService.notify(pr.getRequesterId(), "PAYMENT_REQUEST_DECLINED", "Solicitud de pago rechazada",
                "Tu solicitud de " + pr.getAmount() + " " + pr.getCurrency() + " fue rechazada",
                "PAYMENT_REQUEST", saved.getId());

        return paymentRequestMapper.toResponse(saved);
    }

    public PaymentRequestResponse cancel(String id, String userId) {
        PaymentRequest pr = getAccessibleRequest(id, userId);

        if (!userId.equals(pr.getRequesterId())) {
            throw new UnauthorizedOperationException("Only the requester can cancel this request");
        }
        if (!"PENDING".equalsIgnoreCase(pr.getStatus())) {
            throw new InvalidTransactionException("Only PENDING payment requests can be cancelled");
        }

        PaymentRequest cancelled = pr.toBuilder().status("CANCELLED").build();
        PaymentRequest saved = paymentRequestRepository.save(cancelled);

        return paymentRequestMapper.toResponse(saved);
    }

    /**
     * Barrido periódico que marca {@code EXPIRED} las solicitudes {@code PENDING} cuya
     * fecha de expiración ya pasó — respaldo del chequeo "lazy" que ya hacen
     * {@link #pay}/{@link #decline} (ver {@link #failIfExpired}), para los casos en que
     * nadie vuelve a interactuar con una solicitud vencida.
     */
    @Scheduled(fixedRate = 300_000)
    public void expireOverduePendingRequests() {
        List<PaymentRequest> overdue = paymentRequestRepository.findPendingExpiredBefore(Instant.now());
        for (PaymentRequest pr : overdue) {
            paymentRequestRepository.save(pr.toBuilder().status("EXPIRED").build());
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} overdue pending payment requests", overdue.size());
        }
    }

    /**
     * Si la solicitud sigue {@code PENDING} pero ya venció, la marca {@code EXPIRED}
     * inmediatamente (en vez de esperar al próximo barrido de {@link #expireOverduePendingRequests})
     * y retorna la versión actualizada.
     */
    private PaymentRequest failIfExpired(PaymentRequest pr) {
        if ("PENDING".equalsIgnoreCase(pr.getStatus())
                && pr.getExpiresAt() != null
                && pr.getExpiresAt().isBefore(Instant.now())) {
            return paymentRequestRepository.save(pr.toBuilder().status("EXPIRED").build());
        }
        return pr;
    }

    /**
     * Oculta la existencia del recurso (404) a cualquier usuario que no sea el
     * solicitante ni el pagador designado — mismo criterio IDOR-safe que
     * {@code TransferService.getTransferById}.
     */
    private PaymentRequest getAccessibleRequest(String id, String userId) {
        PaymentRequest pr = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment request not found with ID: " + id));

        if (userId != null
                && !userId.equals(pr.getRequesterId())
                && !userId.equals(pr.getPayerUserId())) {
            throw new ResourceNotFoundException("Payment request not found with ID: " + id);
        }

        return pr;
    }
}
