package com.nexabank.api.service;

import com.nexabank.api.dto.user.UserLookupResponse;
import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.dto.user.UserUpdateRequest;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.UserMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, AccountRepository accountRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.userMapper = userMapper;
    }

    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return userMapper.toResponse(user);
    }

    public UserResponse updateUser(String id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        userMapper.updateFromRequest(request, user);
        User updated = userRepository.update(user);
        return userMapper.toResponse(updated);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Resuelve el destinatario de una transferencia por teléfono: el único
     * dato real que el flujo de transferencia P2P necesita del otro usuario
     * es su {@code accountId} (ver TransferController/CreateTransferRequest
     * — espera un accountId, no un teléfono). {@code /contacts} no sirve
     * para esto: son beneficiarios de transferencia internacional
     * (IBAN/SWIFT), sin accountId interno resoluble.
     */
    public UserLookupResponse lookupByPhone(String phone, String requestingUserId) {
        User recipient = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("No NexaBank user found with that phone number"));

        if (recipient.getId().equals(requestingUserId)) {
            throw new InvalidTransactionException("You cannot send money to yourself");
        }

        Account account = accountRepository.findByUserId(recipient.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("That user has no active account yet"));

        return UserLookupResponse.builder()
                .accountId(account.getId())
                .displayName(recipient.getDisplayName())
                .build();
    }
}
