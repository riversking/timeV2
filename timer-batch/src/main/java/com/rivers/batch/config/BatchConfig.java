package com.rivers.batch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Spring Batch 基础设施配置：Boot 4.0 起 DefaultBatchConfiguration 默认改为 Resourceless（内存版
 * JobRepository，执行记录不落库）。按 Spring Batch 6 官方迁移指南，程序化配置需继承
 * JdbcDefaultBatchConfiguration——其 jobRepository() 为 JDBC 版（BATCH_ 表），数据源取应用上下文中
 * 唯一的 DataSource bean，事务管理器取唯一的 PlatformTransactionManager bean，
 * 使任务执行历史持久化并可被任务管理页面查询
 * </p>
 *
 * <p>
 * 注意：继承 DefaultBatchConfiguration 再叠加 @EnableJdbcJobRepository 的方式在此场景下不生效——
 * BatchRegistrar 先做 containsBeanDefinition("jobRepository") 检查，而父类的 jobRepository()
 * @Bean 方法已先注册（Resourceless），导致 JDBC 版注册被跳过
 * </p>
 *
 * @author rivers
 * @since 2026-10-09
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig extends JdbcDefaultBatchConfiguration {
}
