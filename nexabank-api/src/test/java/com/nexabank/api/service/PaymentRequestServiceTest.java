package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.paymentrequest.CreatePaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PayPaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PaymentRequestResponse;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferService transferService;
    @Mock
    private NotificationService notificationService;

    private PaymentRequestService paymentRequestService;

    @BeforeEach
    void setUp() {
        paymentRequestService = new PaymentRequestService(paymentRequestRepository, userRepository,
                accountRepository, transferService, notificationService, new PaymentRequestMapper());
        ReflectionTestUtils.setField(paymentRequestService, "expirationHours", 48L);
    }

    private PaymentRequest pendingRequest() {
        return PaymentRequest.builder()
                .id("PRQ-00000001").requesterId("USR-REQUESTER").requesterAccountId("ACC-REQUESTER")
                .payerUserId("USR-PAYER").payerPhone("+573000000000")
                .amount(new BigDecimal("50000.00")).currency("COP").status("PENDING")
                .createdAt(Instant.now()).expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void create_shouldSucceed_whenPayerExistsAndAccountOwned() {
        when(userRepository.findByPhone("+573000000000"))
                .thenReturn(Optional.of(User.builder().id("USR-PAYER").build()));
        when(accountRepository.findById("ACC-REQUESTER")).thenReturn(Optional.of(
                Account.builder().id("ACC-REQUESTER").userId("USR-REQUESTER").currency("COP").build()));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> {
            PaymentRequest pr = inv.getArgument(0);
            if (pr.getId() == null) pr.setId("PRQ-00000001");
            return pr;
        });

        CreatePaymentRequestRequest request = CreatePaymentRequestRequest.builder()
                .payerPhone("+573000000000").requesterAccountId("ACC-REQUESTER")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        PaymentRequestResponse result = paymentRequestService.create(request, "USR-REQUESTER");

        assertThat(result.getId()).isEqualTo("PRQ-00000001");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(notificationService).notify(eq("USR-PAYER"), eq("PAYMENT_REQUEST_RECEIVED"),
                anyString(), anyString(), eq("PAYMENT_REQUEST"), eq("PRQ-00000001"));
    }

    @Test
    void create_shouldThrow_whenPayerPhoneNotRegistered() {
        when(userRepository.findByPhone("+573000000000")).thenReturn(Optional.empty());

        CreatePaymentRequestRequest request = CreatePaymentRequestRequest.builder()
                .payerPhone("+573000000000").requesterAccountId("ACC-REQUESTER")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        assertThatThrownBy(() -> paymentRequestService.create(request, "USR-REQUESTER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldThrow_whenRequestingFromSelf() {
        when(userRepository.findByPhone("+573000000000"))
                .thenReturn(Optional.of(User.builder().id("USR-REQUESTER").build()));

        CreatePaymentRequestRequest request = CreatePaymentRequestRequest.builder()
                .payerPhone("+573000000000").requesterAccountId("ACC-REQUESTER")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        assertThatThrownBy(() -> paymentRequestService.create(request, "USR-REQUESTER"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void create_shouldThrowUnauthorized_whenRequesterAccountNotOwned() {
        when(userRepository.findByPhone("+573000000000"))
                .thenReturn(Optional.of(User.builder().id("USR-PAYER").build()));
        when(accountRepository.findById("ACC-REQUESTER")).thenReturn(Optional.of(
                Account.builder().id("ACC-REQUESTER").userId("USR-OTHER").currency("COP").build()));

        CreatePaymentRequestRequest request = CreatePaymentRequestRequest.builder()
                .payerPhone("+573000000000").requesterAccountId("ACC-REQUESTER")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        assertThatThrownBy(() -> paymentRequestService.create(request, "USR-REQUESTER"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void getById_shouldThrowNotFound_whenThirdParty() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> paymentRequestService.getById("PRQ-00000001", "USR-STRANGER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_shouldSucceed_whenRequesterOrPayer() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        assertThat(paymentRequestService.getById("PRQ-00000001", "USR-REQUESTER").getId()).isEqualTo("PRQ-00000001");
        assertThat(paymentRequestService.getById("PRQ-00000001", "USR-PAYER").getId()).isEqualTo("PRQ-00000001");
    }

    @Test
    void pay_shouldSucceed_whenPayerAndPending() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));
        when(accountRepository.findById("ACC-PAYER")).thenReturn(Optional.of(
                Account.builder().id("ACC-PAYER").userId("USR-PAYER").currency("COP").build()));
        when(transferService.executeTransfer(any(), any(), eq("USR-PAYER")))
                .thenReturn(TransferResponse.builder().id("TRF-00000001").status("COMPLETED").build());
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        PaymentRequestResponse result = paymentRequestService.pay("PRQ-00000001", payRequest, null, "USR-PAYER");

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getRelatedTransferId()).isEqualTo("TRF-00000001");
    }

    @Test
    void pay_shouldThrowUnauthorized_whenRequesterTriesToPayOwnRequest() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-X").build();

        assertThatThrownBy(() -> paymentRequestService.pay("PRQ-00000001", payRequest, null, "USR-REQUESTER"))
                .isInstanceOf(UnauthorizedOperationException.class);
        verify(transferService, never()).executeTransfer(any(), any(), any());
    }

    @Test
    void pay_shouldThrowNotFound_whenThirdPartyTriesToPay() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-X").build();

        assertThatThrownBy(() -> paymentRequestService.pay("PRQ-00000001", payRequest, null, "USR-STRANGER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pay_shouldThrow_whenAlreadyPaid() {
        PaymentRequest alreadyPaid = pendingRequest().toBuilder().status("PAID").build();
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(alreadyPaid));

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        assertThatThrownBy(() -> paymentRequestService.pay("PRQ-00000001", payRequest, null, "USR-PAYER"))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transferService, never()).executeTransfer(any(), any(), any());
    }

    @Test
    void pay_shouldMarkExpiredAndThrow_whenExpired() {
        PaymentRequest expired = pendingRequest().toBuilder()
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(expired));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        assertThatThrownBy(() -> paymentRequestService.pay("PRQ-00000001", payRequest, null, "USR-PAYER"))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transferService, never()).executeTransfer(any(), any(), any());
    }

    @Test
    void decline_shouldSucceed_whenPayer() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequestResponse result = paymentRequestService.decline("PRQ-00000001", "USR-PAYER");

        assertThat(result.getStatus()).isEqualTo("DECLINED");
        verify(notificationService).notify(eq("USR-REQUESTER"), eq("PAYMENT_REQUEST_DECLINED"),
                anyString(), anyString(), eq("PAYMENT_REQUEST"), eq("PRQ-00000001"));
    }

    @Test
    void decline_shouldThrowUnauthorized_whenRequesterTriesToDecline() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> paymentRequestService.decline("PRQ-00000001", "USR-REQUESTER"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void cancel_shouldSucceed_whenRequester() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequestResponse result = paymentRequestService.cancel("PRQ-00000001", "USR-REQUESTER");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_shouldThrowUnauthorized_whenPayerTriesToCancel() {
        when(paymentRequestRepository.findById("PRQ-00000001")).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> paymentRequestService.cancel("PRQ-00000001", "USR-PAYER"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void getByUser_shouldQuerySentOrReceived_dependingOnRole() {
        when(paymentRequestRepository.findByRequesterId("USR-000001")).thenReturn(List.of(pendingRequest()));
        when(paymentRequestRepository.findByPayerUserId("USR-000001")).thenReturn(List.of());

        assertThat(paymentRequestService.getByUser("USR-000001", "sent")).hasSize(1);
        assertThat(paymentRequestService.getByUser("USR-000001", "received")).isEmpty();
    }

    @Test
    void expireOverduePendingRequests_shouldMarkExpired() {
        PaymentRequest overdue = pendingRequest().toBuilder()
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        when(paymentRequestRepository.findPendingExpiredBefore(any())).thenReturn(List.of(overdue));
        when(paymentRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentRequestService.expireOverduePendingRequests();

        verify(paymentRequestRepository).save(argThatStatusIs("EXPIRED"));
    }

    private PaymentRequest argThatStatusIs(String status) {
        return org.mockito.ArgumentMatchers.argThat(pr -> status.equals(pr.getStatus()));
    }
}
