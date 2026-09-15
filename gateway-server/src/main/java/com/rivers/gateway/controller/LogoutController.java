package com.rivers.gateway.controller;

import com.rivers.core.constant.SessionConstant;
import com.rivers.core.entity.SessionInfo;
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
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
public class LogoutController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LogoutController(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
        if (authHeader == null || authHeader.length() <= SessionConstant.BEARER_PREFIX.length()
                || !authHeader.regionMatches(true, 0, SessionConstant.BEARER_PREFIX, 0,
                SessionConstant.BEARER_PREFIX.length())) {
            return null;
        }
        return authHeader.substring(SessionConstant.BEARER_PREFIX.length());
    }

    /**
     * 登出 = 整族吊销：删当前 session、活跃窗口、家族指针。
     * familyId 解析优先取会话本体；若持有的是已被轮换的旧 sid（会话键已删），
     * 回退读墓碑 rotated:{sid}（其值即 familyId），确保"多标签页 / 漏读
     * X-New-Session"场景下登出依然彻底。
     */
    private Mono<Void> destroySession(String sessionId) {
        return resolveFamilyId(sessionId)
                .flatMap(this::revokeFamily)
                .then(redisTemplate.delete(SessionConstant.session(sessionId),
                        SessionConstant.active(sessionId)))
                .then()
                .doOnSuccess(v -> log.info("用户登出: sessionId={}", sessionId));
    }

    private Mono<String> resolveFamilyId(String sessionId) {
        return redisTemplate.opsForValue().get(SessionConstant.session(sessionId))
                .filter(StringUtils::isNotBlank)
                .flatMap(json -> Mono.fromCallable(
                                () -> objectMapper.readValue(json, SessionInfo.class).familyId())
                        .subscribeOn(Schedulers.boundedElastic()))
                .filter(StringUtils::isNotBlank)
                .switchIfEmpty(redisTemplate.opsForValue().get(SessionConstant.rotated(sessionId))
                        .filter(StringUtils::isNotBlank));
    }

    /**
     * 吊销家族：删除家族指针指向的当前 sid（session + 活跃窗口），并清空指针
     */
    private Mono<Void> revokeFamily(String familyId) {
        return redisTemplate.opsForValue().get(SessionConstant.family(familyId))
                .filter(StringUtils::isNotBlank)
                .flatMap(currentSid -> redisTemplate.delete(
                        SessionConstant.session(currentSid),
                        SessionConstant.active(currentSid)).then())
                .then(redisTemplate.delete(SessionConstant.family(familyId)))
                .then();
    }
}