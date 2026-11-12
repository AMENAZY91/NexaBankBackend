package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.TransferMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Transfer;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private TransferMapper transferMapper;
    @Mock
    private FxService fxService;
    @Mock
    private FeeService feeService;
    @Mock
    private NotificationService notificationService;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transferRepository, accountRepository,
                transactionRepository, idempotencyRepository, auditLogRepository,
                transferMapper, fxService, feeService, notificationService);
    }

    @Test
    void executeTransfer_shouldSucceed_whenSameCurrencyAndSufficientFunds() {
        String userId = "USR-000001";
        String sourceId = "ACC-000001";
        String destId = "ACC-000002";

        Account source = Account.builder()
                .id(sourceId).userId(userId).currency("COP")
                .balance(new BigDecimal("1000000.00")).status("ACTIVE").build();
        Account dest = Account.builder()
                .id(destId).userId("USR-000002").currency("COP")
                .balance(new BigDecimal("500000.00")).status("ACTIVE").build();

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId(sourceId).destinationAccountId(destId)
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP")
                .description("Test").build();

        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findById(destId)).thenReturn(Optional.of(dest));
        when(feeService.calculateTransferFee(any(), anyString(), anyString()))
                .thenReturn(new BigDecimal("0.00"));
        when(fxService.convert(any())).thenAnswer(inv -> {
            ConvertCurrencyRequest r = (ConvertCurrencyRequest) inv.getArgument(0);
            return com.nexabank.api.dto.fx.ConvertCurrencyResponse.builder()
                    .sourceCurrency(r.getSourceCurrency()).targetCurrency(r.getTargetCurrency())
                    .sourceAmount(r.getAmount()).targetAmount(r.getAmount())
                    .rate(BigDecimal.ONE).fee(BigDecimal.ZERO).total(r.getAmount()).build();
        });
        when(transferRepository.save(any())).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setId("TRF-000001");
            return t;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            com.nexabank.api.model.Transaction t = inv.getArgument(0);
            t.setId("TX-000001");
            return t;
        });
        when(transferMapper.toResponse(any())).thenReturn(TransferResponse.builder()
                .id("TRF-000001").status("COMPLETED").build());

        TransferResponse result = transferService.executeTransfer(request, null, userId);

        assertThat(result.getId()).isEqualTo("TRF-000001");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        // El débito/crédito de saldo se hace atómicamente vía transferBalance (Firestore
        // runTransaction), no con updateBalance independiente — ver AccountRepository.transferBalance.
        verify(accountRepository).transferBalance(sourceId, destId,
                new BigDecimal("200000.00"), new BigDecimal("200000.00"));
        verify(notificationService).notify(eq(userId), eq("TRANSFER_SENT"), anyString(), anyString(),
                eq("TRANSFER"), eq("TRF-000001"));
        verify(notificationService).notify(eq("USR-000002"), eq("TRANSFER_RECEIVED"), anyString(), anyString(),
                eq("TRANSFER"), eq("TRF-000001"));
    }

    @Test
    void executeTransfer_shouldThrowInsufficientFunds_whenBalanceTooLow() {
        String userId = "USR-000001";
        Account source = Account.builder()
                .id("ACC-000001").userId(userId).currency("COP")
                .balance(new BigDecimal("50000.00")).status("ACTIVE").build();
        Account dest = Account.builder()
                .id("ACC-000002").userId("USR-000002").currency("COP")
                .balance(new BigDecimal("500000.00")).status("ACTIVE").build();

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP").build();

        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(source));
        when(accountRepository.findById("ACC-000002")).thenReturn(Optional.of(dest));
        when(feeService.calculateTransferFee(any(), anyString(), anyString()))
                .thenReturn(BigDecimal.ZERO);
        when(fxService.convert(any())).thenAnswer(inv -> {
            ConvertCurrencyRequest r = (ConvertCurrencyRequest) inv.getArgument(0);
            return com.nexabank.api.dto.fx.ConvertCurrencyResponse.builder()
                    .sourceCurrency(r.getSourceCurrency()).targetCurrency(r.getTargetCurrency())
                    .sourceAmount(r.getAmount()).targetAmount(r.getAmount())
                    .rate(BigDecimal.ONE).fee(BigDecimal.ZERO).total(r.getAmount()).build();
        });

        assertThatThrownBy(() -> transferService.executeTransfer(request, null, userId))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void executeTransfer_shouldThrowUnauthorized_whenSourceNotOwnedByUser() {
        String userId = "USR-000001";
        Account source = Account.builder()
                .id("ACC-000001").userId("USR-000002").currency("COP")
                .balance(new BigDecimal("1000000.00")).status("ACTIVE").build();

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP").build();

        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> transferService.executeTransfer(request, null, userId))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void executeTransfer_shouldReturnExisting_whenIdempotentKeyCompleted() {
        String userId = "USR-000001";
        String idempotencyKey = "key-123";
        Transfer existing = Transfer.builder().id("TRF-000001").status("COMPLETED")
                .sourceUserId(userId).destinationUserId("USR-000002").build();

        when(idempotencyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(IdempotencyKey.builder()
                        .key(idempotencyKey).status("COMPLETED")
                        .responseReference("TRF-000001").build()));
        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(existing));
        when(transferMapper.toResponse(existing)).thenReturn(TransferResponse.builder()
                .id("TRF-000001").status("COMPLETED").build());

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("100.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP").build();

        TransferResponse result = transferService.executeTransfer(request, idempotencyKey, userId);

        assertThat(result.getId()).isEqualTo("TRF-000001");
        verify(accountRepository, never()).findById(anyString());
    }

    @Test
    void cancelTransfer_shouldSucceed_whenPendingAndOwned() {
        String userId = "USR-000001";
        Transfer pending = Transfer.builder()
                .id("TRF-000001").sourceUserId(userId).status("PENDING")
                .amount(new BigDecimal("100.00")).build();

        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(pending));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferMapper.toResponse(any())).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            return TransferResponse.builder().id(t.getId()).status(t.getStatus()).build();
        });

        TransferResponse result = transferService.cancelTransfer("TRF-000001", userId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelTransfer_shouldThrowUnauthorized_whenNotOwned() {
        Transfer pending = Transfer.builder()
                .id("TRF-000001").sourceUserId("USR-000002").status("PENDING").build();

        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> transferService.cancelTransfer("TRF-000001", "USR-000001"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void cancelTransfer_shouldThrow_whenNotPending() {
        Transfer completed = Transfer.builder()
                .id("TRF-000001").sourceUserId("USR-000001").status("COMPLETED").build();

        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> transferService.cancelTransfer("TRF-000001", "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void getTransfersByUser_shouldReturnList() {
        when(transferRepository.findByUserId("USR-000001")).thenReturn(List.of());

        var result = transferService.getTransfersByUser("USR-000001");

        assertThat(result).isEmpty();
    }

    @Test
    void getTransferById_shouldThrowNotFound_whenMissing() {
        when(transferRepository.findById("TRF-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransferById("TRF-999999", "USR-000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTransferById_shouldReturn_whenUserIsSource() {
        Transfer transfer = Transfer.builder()
                .id("TRF-000001").sourceUserId("USR-000001").destinationUserId("USR-000002").build();
        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(transfer));
        when(transferMapper.toResponse(transfer)).thenReturn(TransferResponse.builder().id("TRF-000001").build());

        TransferResponse result = transferService.getTransferById("TRF-000001", "USR-000001");

        assertThat(result.getId()).isEqualTo("TRF-000001");
    }

    @Test
    void getTransferById_shouldReturn_whenUserIsDestination() {
        Transfer transfer = Transfer.builder()
                .id("TRF-000001").sourceUserId("USR-000001").destinationUserId("USR-000002").build();
        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(transfer));
        when(transferMapper.toResponse(transfer)).thenReturn(TransferResponse.builder().id("TRF-000001").build());

        TransferResponse result = transferService.getTransferById("TRF-000001", "USR-000002");

        assertThat(result.getId()).isEqualTo("TRF-000001");
    }

    @Test
    void getTransferById_shouldThrowNotFound_whenUserIsNeitherSourceNorDestination() {
        Transfer transfer = Transfer.builder()
                .id("TRF-000001").sourceUserId("USR-000001").destinationUserId("USR-000002").build();
        when(transferRepository.findById("TRF-000001")).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transferService.getTransferById("TRF-000001", "USR-999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
