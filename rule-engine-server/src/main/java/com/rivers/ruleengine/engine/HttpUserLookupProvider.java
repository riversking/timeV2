package com.rivers.ruleengine.engine;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.UserDetailRes;
import com.rivers.proto.UserReq;
import com.rivers.ruleengine.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 用户存在性查询默认实现：经 {@code @HttpExchange} 声明式客户端（UserServiceClient）
 * 调 user-server /user/getUserDetail。
 * <p>
 * 服务地址见 UserServiceClient 的 @HttpExchange（http://user-server，经 nacos 服务发现解析）；
 * 超时由 rule-engine.user-lookup.timeout-ms 控制（默认 3000ms，需覆盖首次调用的服务发现冷启动开销）。
 * 响应 ResultVO：code=200 且 data 非空 → 用户存在。
 * 超时/服务不可用/查询失败 → false（降级保留字面，不抛错不影响流程）。
 */
@Component
@Slf4j
public class HttpUserLookupProvider implements UserLookupProvider {

    private final UserServiceClient userServiceClient;
    private final Duration timeout;

    public HttpUserLookupProvider(UserServiceClient userServiceClient,
            @Value("${rule-engine.user-lookup.timeout-ms:3000}") long timeoutMs) {
        this.userServiceClient = userServiceClient;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Override
    public Mono<Boolean> exists(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.just(false);
        }
        return userServiceClient.getUserDetail(UserReq.newBuilder().setUserId(userId).build())
                .timeout(timeout)
                .map(this::found)
                .onErrorResume(e -> {
                    log.debug("[UserLookup] 查询降级 userId={}, cause={}", userId, e.toString());
                    return Mono.just(false);
                });
    }

    /** ResultVO：code=200 且 data 非空 → 用户存在 */
    private boolean found(ResultVO<UserDetailRes> result) {
        return result != null && Integer.valueOf(200).equals(result.getCode()) && result.getData() != null;
    }
}
