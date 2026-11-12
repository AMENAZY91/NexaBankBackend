package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.card.CardResponse;
import com.nexabank.api.dto.card.CreateCardRequest;
import com.nexabank.api.dto.card.UpdateCardRequest;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.CardMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.Card;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.CardRepository;
import com.nexabank.api.service.provider.CardIssuer;
import com.nexabank.api.service.provider.CardLimits;
import com.nexabank.api.service.provider.IssuedCard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CardIssuer cardIssuer;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogRepository auditLogRepository;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository, accountRepository, cardIssuer,
                notificationService, auditLogRepository, new CardMapper());
        ReflectionTestUtils.setField(cardService, "defaultDailyLimit", new BigDecimal("2000000"));
        ReflectionTestUtils.setField(cardService, "defaultMonthlyLimit", new BigDecimal("20000000"));
    }

    private Account activeAccount() {
        return Account.builder().id("ACC-000001").userId("USR-000001").currency("COP").status("ACTIVE").build();
    }

    @Test
    void issue_shouldSucceed_andApplyDefaultLimits_whenNoneRequested() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount()));
        when(cardIssuer.issue(any())).thenReturn(new IssuedCard("tok-abc", "VIRTUAL", "5678", 12, 2029));
        when(cardRepository.save(any())).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            if (c.getId() == null) c.setId("CARD-00000001");
            return c;
        });

        CreateCardRequest request = CreateCardRequest.builder().accountId("ACC-000001").build();

        CardResponse result = cardService.issue(request, "USR-000001");

        assertThat(result.getId()).isEqualTo("CARD-00000001");
        assertThat(result.getLast4()).isEqualTo("5678");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getDailyLimit()).isEqualTo(new BigDecimal("2000000"));
        verify(notificationService).notify(eq("USR-000001"), eq("CARD_ISSUED"), any(), any(), eq("CARD"), eq("CARD-00000001"));
    }

    @Test
    void issue_shouldUseRequestedLimits_whenProvided() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount()));
        when(cardIssuer.issue(any())).thenReturn(new IssuedCard("tok-abc", "VIRTUAL", "5678", 12, 2029));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCardRequest request = CreateCardRequest.builder()
                .accountId("ACC-000001").dailyLimit(new BigDecimal("500000")).monthlyLimit(new BigDecimal("5000000")).build();

        CardResponse result = cardService.issue(request, "USR-000001");

        assertThat(result.getDailyLimit()).isEqualTo(new BigDecimal("500000"));
        assertThat(result.getMonthlyLimit()).isEqualTo(new BigDecimal("5000000"));
    }

    @Test
    void issue_shouldThrowUnauthorized_whenAccountNotOwned() {
        Account other = Account.builder().id("ACC-000001").userId("USR-999999").status("ACTIVE").build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(other));

        CreateCardRequest request = CreateCardRequest.builder().accountId("ACC-000001").build();

        assertThatThrownBy(() -> cardService.issue(request, "USR-000001"))
                .isInstanceOf(UnauthorizedOperationException.class);
        verify(cardIssuer, never()).issue(any());
    }

    @Test
    void getCardById_shouldThrowNotFound_whenNotOwner() {
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(
                Card.builder().id("CARD-00000001").userId("USR-000001").build()));

        assertThatThrownBy(() -> cardService.getCardById("CARD-00000001", "USR-999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_shouldBlockCard_andCallIssuerBlock() {
        Card active = Card.builder().id("CARD-00000001").userId("USR-000001").status("ACTIVE")
                .issuerToken("tok-abc").last4("5678").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(active));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCardRequest request = UpdateCardRequest.builder().status("BLOCKED").build();
        CardResponse result = cardService.update("CARD-00000001", request, "USR-000001");

        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        verify(cardIssuer).block("tok-abc");
    }

    @Test
    void update_shouldUnblockCard_andCallIssuerUnblock() {
        Card blocked = Card.builder().id("CARD-00000001").userId("USR-000001").status("BLOCKED")
                .issuerToken("tok-abc").last4("5678").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(blocked));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCardRequest request = UpdateCardRequest.builder().status("ACTIVE").build();
        CardResponse result = cardService.update("CARD-00000001", request, "USR-000001");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(cardIssuer).unblock("tok-abc");
    }

    @Test
    void update_shouldUpdateLimits_andCallIssuerSetLimits() {
        Card active = Card.builder().id("CARD-00000001").userId("USR-000001").status("ACTIVE")
                .issuerToken("tok-abc").dailyLimit(new BigDecimal("100")).monthlyLimit(new BigDecimal("1000")).build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(active));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCardRequest request = UpdateCardRequest.builder().dailyLimit(new BigDecimal("300")).build();
        CardResponse result = cardService.update("CARD-00000001", request, "USR-000001");

        assertThat(result.getDailyLimit()).isEqualTo(new BigDecimal("300"));
        assertThat(result.getMonthlyLimit()).isEqualTo(new BigDecimal("1000")); // sin cambios
        verify(cardIssuer).setLimits(eq("tok-abc"), eq(new CardLimits(new BigDecimal("300"), new BigDecimal("1000"))));
    }

    @Test
    void update_shouldThrow_whenCardCancelled() {
        Card cancelled = Card.builder().id("CARD-00000001").userId("USR-000001").status("CANCELLED").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(cancelled));

        UpdateCardRequest request = UpdateCardRequest.builder().status("ACTIVE").build();

        assertThatThrownBy(() -> cardService.update("CARD-00000001", request, "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void cancel_shouldSucceed_whenActive() {
        Card active = Card.builder().id("CARD-00000001").userId("USR-000001").status("ACTIVE").last4("5678").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(active));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardService.cancel("CARD-00000001", "USR-000001");

        verify(notificationService).notify(eq("USR-000001"), eq("CARD_CANCELLED"), any(), any(), eq("CARD"), eq("CARD-00000001"));
    }

    @Test
    void cancel_shouldThrow_whenAlreadyCancelled() {
        Card cancelled = Card.builder().id("CARD-00000001").userId("USR-000001").status("CANCELLED").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> cardService.cancel("CARD-00000001", "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void cancel_shouldThrowNotFound_whenNotOwner() {
        Card active = Card.builder().id("CARD-00000001").userId("USR-000001").status("ACTIVE").build();
        when(cardRepository.findById("CARD-00000001")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> cardService.cancel("CARD-00000001", "USR-999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCardsByUser_shouldReturnList() {
        when(cardRepository.findByUserId("USR-000001")).thenReturn(List.of());

        assertThat(cardService.getCardsByUser("USR-000001")).isEmpty();
    }
}
