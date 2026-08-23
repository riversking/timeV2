package com.rivers.gateway.controller;

import cn.hutool.json.JSONUtil;
import com.rivers.core.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
public class LogoutController {

    private static final String SESSION_PREFIX = "session:";
    private static final String ACTIVE_PREFIX = "session:last:";
    private static final String FAMILY_PREFIX = "session:family:";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ReactiveStringRedisTemplate redisTemplate;

    public LogoutController(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("logout")
    public Mono<ResultVO<String>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        return Mono.justOrEmpty(extractSessionId(authHeader))
                .filter(StringUtils::isNotBlank)
                .flatMap(this::destroySession)
                .thenReturn(ResultVO.ok("已登出"));
    }

    private String extractSessionId(String authHeader) {
        if (authHeader == null || authHeader.length() <= BEARER_PREFIX.length()
                || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    /**
     * 登出 = 整族吊销：删当前 session、活跃窗口、家族指针。
     * 家族内其他 sid 因家族指针失效，无法再通过轮换/宽限路径续命。
     */
    private Mono<Void> destroySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .filter(StringUtils::isNotBlank)
                .flatMap(json -> Mono.fromCallable(() ->
                                JSONUtil.parseObj(json).getStr("familyId"))
                        .subscribeOn(Schedulers.boundedElastic()))
                .filter(StringUtils::isNotBlank)
                .flatMap(familyId -> redisTemplate.opsForValue().get(FAMILY_PREFIX + familyId)
                        .filter(StringUtils::isNotBlank)
                        .flatMap(currentSid -> redisTemplate.delete(
                                SESSION_PREFIX + currentSid,
                                ACTIVE_PREFIX + currentSid,
                                FAMILY_PREFIX + familyId).then()))
                .then(redisTemplate.delete(SESSION_PREFIX + sessionId, ACTIVE_PREFIX + sessionId))
                .then()
                .doOnSuccess(v -> log.info("用户登出: sessionId={}", sessionId));
    }
}