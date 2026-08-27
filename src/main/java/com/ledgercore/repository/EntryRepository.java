package com.ledgercore.repository;

import com.ledgercore.entity.Entry;
import com.ledgercore.entity.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    @Query("""
                SELECT COALESCE(
                    SUM(
                        CASE
                            WHEN e.type = :credit THEN e.amount
                            ELSE -e.amount
                        END
                    ),
                    0
                )
                FROM Entry e
                WHERE e.account.id = :accountId
                  AND e.transaction.status IN (
                      com.ledgercore.entity.TransactionStatus.COMPLETED,
                      com.ledgercore.entity.TransactionStatus.REVERSED
                  )
            """)
    BigDecimal calculateBalance(
            @Param("accountId") UUID accountId,
            @Param("credit") EntryType credit);

    @Query("SELECT e FROM Entry e WHERE e.account.id = :accountId")
    List<Entry> findByAccountId(@Param("accountId") UUID accountId);
}