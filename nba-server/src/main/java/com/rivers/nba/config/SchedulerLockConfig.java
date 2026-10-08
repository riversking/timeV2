package com.rivers.nba.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.lettuce.LettuceLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * 定时任务分布式锁配置：多实例部署时，同一任务的同一时刻仅有一个实例执行
 * （锁存储于 Redis，由 ShedLock 提供互斥语义）
 * </p>
 *
 * @author rivers
 * @since 2026-10-09
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "2m")
public class SchedulerLockConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 分布式锁专用 Lettuce 客户端（独立于业务 Redis 模板，互不影响）
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient shedLockRedisClient() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase);
        if (StringUtils.isNotBlank(redisPassword)) {
            builder.withPassword(redisPassword);
        }
        return RedisClient.create(builder.build());
    }

    /**
     * 锁用的共享连接（随客户端创建，关闭时先于客户端释放）
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> shedLockRedisConnection(RedisClient shedLockRedisClient) {
        return shedLockRedisClient.connect();
    }

    /**
     * 基于 Redis 的锁提供者
     */
    @Bean
    public LockProvider lockProvider(StatefulRedisConnection<String, String> shedLockRedisConnection) {
        return new LettuceLockProvider(shedLockRedisConnection);
    }
}
