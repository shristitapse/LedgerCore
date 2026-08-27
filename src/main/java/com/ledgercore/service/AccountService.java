package com.ledgercore.service;

import com.ledgercore.dto.BalanceResponse;
import com.ledgercore.entity.Account;
import com.ledgercore.entity.EntryType;
import com.ledgercore.exception.AccountNotFoundException;
import com.ledgercore.repository.AccountRepository;
import com.ledgercore.repository.EntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final EntryRepository entryRepository;

    public AccountService(
            AccountRepository accountRepository,
            EntryRepository entryRepository) {

        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public Account createAccount(String name) {
        Account account = new Account(name.trim());
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(
                    "Account not found: " + accountId);
        }

        var balance = entryRepository.calculateBalance(
                accountId, EntryType.CREDIT);

        return new BalanceResponse(accountId, balance);
    }
}