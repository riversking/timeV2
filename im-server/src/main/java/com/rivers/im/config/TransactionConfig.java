package com.rivers.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class TransactionConfig {

    /**
     * 响应式事务操作符：R2DBC 多步写用 .as(txOperator::transactional) 包裹，
     * 保证多条 SQL 要么全部提交要么全部回滚。
     * 事务管理器由 Spring Boot R2DBC 自动配置提供。
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}