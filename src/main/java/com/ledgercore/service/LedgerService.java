package com.ledgercore.service;

import com.ledgercore.dto.CreateTransactionRequest;
import com.ledgercore.entity.*;
import com.ledgercore.exception.*;
import com.ledgercore.repository.AccountRepository;
import com.ledgercore.repository.EntryRepository;
import com.ledgercore.repository.LedgerTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LedgerService {

        private final AccountRepository accountRepository;
        private final LedgerTransactionRepository transactionRepository;
        private final EntryRepository entryRepository;

        public LedgerService(
                        AccountRepository accountRepository,
                        LedgerTransactionRepository transactionRepository,
                        EntryRepository entryRepository) {

                this.accountRepository = accountRepository;
                this.transactionRepository = transactionRepository;
                this.entryRepository = entryRepository;
        }

        @Transactional
        public LedgerTransaction createTransaction(CreateTransactionRequest request) {

                // 1. Idempotency check
                Optional<LedgerTransaction> existing = transactionRepository
                                .findByIdempotencyKey(request.getIdempotencyKey());

                if (existing.isPresent()) {
                        return existing.get();
                }

                // 2. Validate entries structure
                validateEntries(request.getEntries());

                // 3. Lock accounts in deterministic UUID order to prevent deadlocks
                List<UUID> accountIds = request.getEntries().stream()
                                .map(CreateTransactionRequest.EntryRequest::getAccountId)
                                .distinct()
                                .sorted()
                                .toList();

                List<Account> lockedAccounts = accountRepository.findAllByIdForUpdate(accountIds);

                // Verify all accounts exist
                if (lockedAccounts.size() != accountIds.size()) {
                        Set<UUID> foundIds = lockedAccounts.stream()
                                        .map(Account::getId)
                                        .collect(Collectors.toSet());

                        UUID missing = accountIds.stream()
                                        .filter(id -> !foundIds.contains(id))
                                        .findFirst()
                                        .orElse(null);

                        throw new AccountNotFoundException(
                                        "Account not found: " + missing);
                }

                Map<UUID, Account> accountMap = lockedAccounts.stream()
                                .collect(Collectors.toMap(Account::getId, a -> a));

                // 4. Create transaction
                LedgerTransaction transaction = new LedgerTransaction(request.getIdempotencyKey());

                // 5. Create entries
                for (CreateTransactionRequest.EntryRequest entryReq : request.getEntries()) {
                        Account account = accountMap.get(entryReq.getAccountId());
                        Entry entry = new Entry(
                                        account,
                                        entryReq.getAmount(),
                                        entryReq.getType());
                        transaction.addEntry(entry);
                }

                // 6. Mark completed
                transaction.complete();

                // 7. Persist atomically
                try {
                        return transactionRepository.save(transaction);
                } catch (DataIntegrityViolationException ex) {
                        return transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                                        .orElseThrow(() -> ex);
                }
        }

        @Transactional(readOnly = true)
        public LedgerTransaction getTransaction(UUID id) {
                return transactionRepository.findByIdWithEntries(id)
                                .orElseThrow(() -> new TransactionNotFoundException(
                                                "Transaction not found: " + id));
        }

        @Transactional
        public LedgerTransaction reverse(UUID transactionId) {

                LedgerTransaction original = transactionRepository.findById(transactionId)
                                .orElseThrow(() -> new TransactionNotFoundException(
                                                "Transaction not found: " + transactionId));

                if (original.getStatus() == TransactionStatus.PENDING) {
                        throw new InvalidReversalException(
                                        "Cannot reverse a PENDING transaction");
                }

                if (original.getStatus() == TransactionStatus.REVERSED) {
                        throw new InvalidReversalException(
                                        "Transaction is already reversed");
                }

                // Idempotent reversal
                String reversalKey = "REVERSAL-" + original.getId();

                Optional<LedgerTransaction> existingReversal = transactionRepository.findByIdempotencyKey(reversalKey);

                if (existingReversal.isPresent()) {
                        return existingReversal.get();
                }

                // Lock accounts in deterministic order
                List<UUID> accountIds = original.getEntries().stream()
                                .map(e -> e.getAccount().getId())
                                .distinct()
                                .sorted()
                                .toList();

                accountRepository.findAllByIdForUpdate(accountIds);

                // Create reversal transaction with opposite entries
                LedgerTransaction reversal = new LedgerTransaction(reversalKey);

                for (Entry originalEntry : original.getEntries()) {
                        EntryType reversedType = originalEntry.getType() == EntryType.DEBIT
                                        ? EntryType.CREDIT
                                        : EntryType.DEBIT;

                        Entry reversedEntry = new Entry(
                                        originalEntry.getAccount(),
                                        originalEntry.getAmount(),
                                        reversedType);
                        reversal.addEntry(reversedEntry);
                }

                reversal.complete();

                LedgerTransaction savedReversal;
                try {
                        savedReversal = transactionRepository.save(reversal);
                } catch (DataIntegrityViolationException ex) {
                        savedReversal = transactionRepository.findByIdempotencyKey(reversalKey)
                                        .orElseThrow(() -> ex);
                }

                original.setStatus(TransactionStatus.REVERSED);
                original.setReversedTransactionId(savedReversal.getId());
                transactionRepository.save(original);

                return savedReversal;
        }

        @Transactional(readOnly = true)
        public List<LedgerTransaction> getTransactionHistory(UUID accountId) {
                if (!accountRepository.existsById(accountId)) {
                        throw new AccountNotFoundException(
                                        "Account not found: " + accountId);
                }
                return transactionRepository.findByAccountId(accountId);
        }

        private void validateEntries(
                        List<CreateTransactionRequest.EntryRequest> entries) {

                boolean hasDebit = entries.stream()
                                .anyMatch(e -> e.getType() == EntryType.DEBIT);
                boolean hasCredit = entries.stream()
                                .anyMatch(e -> e.getType() == EntryType.CREDIT);

                if (!hasDebit) {
                        throw new InvalidTransactionException(
                                        "Transaction must contain at least one DEBIT entry");
                }
                if (!hasCredit) {
                        throw new InvalidTransactionException(
                                        "Transaction must contain at least one CREDIT entry");
                }

                BigDecimal totalDebits = entries.stream()
                                .filter(e -> e.getType() == EntryType.DEBIT)
                                .map(CreateTransactionRequest.EntryRequest::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalCredits = entries.stream()
                                .filter(e -> e.getType() == EntryType.CREDIT)
                                .map(CreateTransactionRequest.EntryRequest::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalDebits.compareTo(totalCredits) != 0) {
                        throw new UnbalancedTransactionException(
                                        "Debit and credit totals must be equal. " +
                                                        "Debits: " + totalDebits + ", Credits: " + totalCredits);
                }
        }
}
