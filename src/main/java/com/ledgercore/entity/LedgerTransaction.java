package com.ledgercore.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transaction_idempotency_key", columnNames = "idempotency_key")
})
public class LedgerTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reversed_transaction_id")
    private UUID reversedTransactionId;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Entry> entries = new ArrayList<>();

    protected LedgerTransaction() {
    }

    public LedgerTransaction(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING transactions can be completed");
        }
        this.status = TransactionStatus.COMPLETED;
    }

    public void addEntry(Entry entry) {
        entries.add(entry);
        entry.setTransaction(this);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getReversedTransactionId() {
        return reversedTransactionId;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public void setReversedTransactionId(UUID reversedTransactionId) {
        this.reversedTransactionId = reversedTransactionId;
    }
}