package com.appreseal.repo;

import com.appreseal.domain.TxType;
import com.appreseal.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    boolean existsByRefIdAndType(String refId, TxType type);

    List<Transaction> findByTypeAndCreatedAtBetween(TxType type, Instant from, Instant to);

    @Query("""
            select coalesce(sum(t.amountCents), 0) from Transaction t
            where t.type = :type and t.createdAt >= :from and t.createdAt < :to
            """)
    long sumByTypeBetween(@Param("type") TxType type, @Param("from") Instant from, @Param("to") Instant to);
}
