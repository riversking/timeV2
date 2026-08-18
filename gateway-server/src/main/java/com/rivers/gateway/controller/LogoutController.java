package com.rivers.gateway.controller;

import cn.hutool.json.JSONUtil;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class LogoutController {

    private static final String SESSION_PREFIX = "session:";
    private static final String TOKEN_PREFIX = "token:";
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

    private Mono<Void> destroySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .filter(StringUtils::isNotBlank)
                .flatMap(sessionJson ->
                        Mono.justOrEmpty(JSONUtil.parseObj(sessionJson).getStr("accessToken"))
                                .filter(StringUtils::isNotBlank)
                                .flatMap(this::deleteToken)
                                .then(redisTemplate.delete(SESSION_PREFIX + sessionId).then())
                                .doOnSuccess(v ->
                                        log.info("用户登出: sessionId={}", sessionId))
                );
    }

    private Mono<Void> deleteToken(String token) {
        return Mono.justOrEmpty(JwtUtil.parseJwt(token))
                .filter(claims -> claims.getId() != null)
                .flatMap(claims ->
                        redisTemplate.delete(TOKEN_PREFIX + claims.getId()).then());
    }
}