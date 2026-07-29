package com.rivers.gateway.controller;

import cn.hutool.json.JSONUtil;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@RestController
public class LogoutController {

    private static final String COOKIE_SESSION = "SESSION_ID";
    private static final String SESSION_PREFIX = "session:";
    private static final String TOKEN_PREFIX = "token:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public LogoutController(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("logout")
    public Mono<ResultVO<String>> logout(
            @CookieValue(value = COOKIE_SESSION, required = false) String sessionId,
            ServerHttpResponse response) {
        return Mono.justOrEmpty(sessionId)
                .filter(StringUtils::isNotBlank)
                .flatMap(this::destroySession)
                .then(Mono.fromRunnable(() -> clearCookie(response)))
                .thenReturn(ResultVO.ok("已登出"));
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

    private void clearCookie(ServerHttpResponse response) {
        response.addCookie(ResponseCookie.from(COOKIE_SESSION, "")
                .httpOnly(true).path("/").maxAge(Duration.ZERO).build());
    }
}