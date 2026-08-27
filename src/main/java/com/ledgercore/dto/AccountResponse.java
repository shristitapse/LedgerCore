package com.ledgercore.dto;

import com.ledgercore.entity.Account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        Instant createdAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getCreatedAt());
    }
}
