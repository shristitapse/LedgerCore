package com.ledgercore.dto;

import com.ledgercore.entity.EntryType;
import com.ledgercore.entity.LedgerTransaction;
import com.ledgercore.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
                UUID id,
                String idempotencyKey,
                TransactionStatus status,
                Instant createdAt,
                UUID reversedTransactionId,
                List<EntryResponse> entries) {

        public record EntryResponse(
                        UUID accountId,
                        BigDecimal amount,
                        EntryType type) {
        }

        public static TransactionResponse from(LedgerTransaction transaction) {

                List<EntryResponse> entries = transaction.getEntries()
                                .stream()
                                .map(entry -> new EntryResponse(
                                                entry.getAccount().getId(),
                                                entry.getAmount(),
                                                entry.getType()))
                                .toList();

                return new TransactionResponse(
                                transaction.getId(),
                                transaction.getIdempotencyKey(),
                                transaction.getStatus(),
                                transaction.getCreatedAt(),
                                transaction.getReversedTransactionId(),
                                entries);
        }
}