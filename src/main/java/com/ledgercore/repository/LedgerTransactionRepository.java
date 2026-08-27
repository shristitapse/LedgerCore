package com.ledgercore.repository;

import com.ledgercore.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID> {

    @Query("""
                SELECT DISTINCT t FROM LedgerTransaction t
                LEFT JOIN FETCH t.entries e
                LEFT JOIN FETCH e.account
                WHERE t.idempotencyKey = :idempotencyKey
            """)
    Optional<LedgerTransaction> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("""
                SELECT DISTINCT t FROM LedgerTransaction t
                JOIN FETCH t.entries e
                JOIN FETCH e.account
                WHERE t.id = :id
            """)
    Optional<LedgerTransaction> findByIdWithEntries(@Param("id") UUID id);

    @Query("""
                SELECT DISTINCT t FROM LedgerTransaction t
                JOIN FETCH t.entries e
                JOIN FETCH e.account
                WHERE e.account.id = :accountId
                ORDER BY t.createdAt DESC
            """)
    List<LedgerTransaction> findByAccountId(@Param("accountId") UUID accountId);
}
