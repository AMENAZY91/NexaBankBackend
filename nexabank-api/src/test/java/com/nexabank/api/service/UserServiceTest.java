package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.user.UserLookupResponse;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.UserMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, accountRepository, userMapper);
    }

    @Test
    void lookupByPhone_shouldReturnAccountIdAndDisplayName_whenRecipientExists() {
        User recipient = User.builder().id("USR-RECIPIENT").displayName("Rodrigo Salazar").build();
        Account account = Account.builder().id("ACC-RECIPIENT").userId("USR-RECIPIENT").build();
        when(userRepository.findByPhone("+51999000002")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUserId("USR-RECIPIENT")).thenReturn(List.of(account));

        UserLookupResponse result = userService.lookupByPhone("+51999000002", "USR-SENDER");

        assertThat(result.getAccountId()).isEqualTo("ACC-RECIPIENT");
        assertThat(result.getDisplayName()).isEqualTo("Rodrigo Salazar");
    }

    @Test
    void lookupByPhone_shouldThrowNotFound_whenNoUserWithThatPhone() {
        when(userRepository.findByPhone("+51999000099")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.lookupByPhone("+51999000099", "USR-SENDER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lookupByPhone_shouldThrowNotFound_whenRecipientHasNoAccount() {
        User recipient = User.builder().id("USR-RECIPIENT").displayName("Rodrigo Salazar").build();
        when(userRepository.findByPhone("+51999000002")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUserId("USR-RECIPIENT")).thenReturn(List.of());

        assertThatThrownBy(() -> userService.lookupByPhone("+51999000002", "USR-SENDER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lookupByPhone_shouldThrowInvalidTransaction_whenLookingUpSelf() {
        User self = User.builder().id("USR-SENDER").displayName("Self").build();
        when(userRepository.findByPhone("+51999000001")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> userService.lookupByPhone("+51999000001", "USR-SENDER"))
                .isInstanceOf(InvalidTransactionException.class);
    }
}
