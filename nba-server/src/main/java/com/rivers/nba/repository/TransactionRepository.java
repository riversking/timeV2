package com.rivers.nba.repository;

import com.rivers.nba.entity.TransactionRecord;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 交易记录表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface TransactionRepository extends ReactiveCrudRepository<TransactionRecord, Long> {

    @Query("SELECT * FROM transaction_record WHERE is_deleted = 0 ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<TransactionRecord> pageAll(int limit, long offset);

    @Query("SELECT COUNT(*) FROM transaction_record WHERE is_deleted = 0")
    Mono<Long> countAll();

    @Query("SELECT * FROM transaction_record WHERE is_deleted = 0 AND DATE(date) = :date ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<TransactionRecord> pageByDate(String date, int limit, long offset);

    @Query("SELECT COUNT(*) FROM transaction_record WHERE is_deleted = 0 AND DATE(date) = :date")
    Mono<Long> countByDate(String date);

    @Modifying
    @Query("UPDATE transaction_record SET is_deleted = 1 WHERE is_deleted = 0 AND DATE(date) = :date")
    Mono<Long> logicDeleteByDate(String date);
}
