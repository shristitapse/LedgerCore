package com.ledgercore.dto;

import com.ledgercore.entity.EntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class CreateTransactionRequest {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotNull(message = "Entries are required")
    @Size(min = 2, message = "At least 2 entries are required")
    @Valid
    private List<EntryRequest> entries;

    public CreateTransactionRequest() {
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<EntryRequest> getEntries() {
        return entries;
    }

    public void setEntries(List<EntryRequest> entries) {
        this.entries = entries;
    }

    public static class EntryRequest {

        @NotNull(message = "Account ID is required")
        private UUID accountId;

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        private BigDecimal amount;

        @NotNull(message = "Entry type is required")
        private EntryType type;

        public EntryRequest() {
        }

        public UUID getAccountId() {
            return accountId;
        }

        public void setAccountId(UUID accountId) {
            this.accountId = accountId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public EntryType getType() {
            return type;
        }

        public void setType(EntryType type) {
            this.type = type;
        }
    }
}
