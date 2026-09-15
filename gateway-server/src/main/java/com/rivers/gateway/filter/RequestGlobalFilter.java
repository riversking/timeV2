package com.rivers.gateway.filter;

import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.constant.SessionConstant;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.entity.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关统一鉴权 — 纯 Session Bearer 模式（无 JWT / 无 Lua）
 * <p>
 * 会话键名 / TTL / 轮换周期契约见 {@link SessionConstant}，会话 JSON 结构见 {@link SessionInfo}
 * （由 rivers-core 统一定义，user-server 创建，网关轮换 / 宽限 / 吊销）。
 * <p>
 * 轮换：每 12h（rotateAt 到期）以"墓碑 SETNX"抢占轮换权，赢家才推进家族指针并删旧键，
 * 新 sid 通过响应头 X-New-Session 下发；旧 sid 重放 → 回滚自愈 / 宽限限次补发 / 整族吊销（终局）。
 * <p>
 * Body 重写安全：JSON 预检 + Content-Length 预检透传 + 有上限 join（超限 413）+ 读超时（408）。
 *
 * @author riversking
 */
@Component
@Slf4j
@NullMarked
public class RequestGlobalFilter implements WebFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"message\":\"鉴权失败\"}";
    /**
     * 宽限窗口内允许的补发次数上限（容忍轮换瞬间的并发 / 多标签页突发）
     */
    private static final long GRACE_MAX_GRANTS = 10L;
    private static final String ATTR_LOGIN_USER = "gateway.loginUser";
    public static final String LOGIN_USER = "loginUser";
    private static final int MAX_BODY_BUFFER_SIZE = 1024 * 1024;
    private static final Duration BODY_READ_TIMEOUT = Duration.ofSeconds(30);

    private final FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RequestGlobalFilter(FilterIgnorePropertiesConfig filterIgnorePropertiesConfig,
                               ReactiveStringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.filterIgnorePropertiesConfig = filterIgnorePropertiesConfig;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  主入口
    // ═══════════════════════════════════════════════════════════════
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getPath().value();
        var ignoreUrls = filterIgnorePropertiesConfig.getUrls();
        if (ignoreUrls != null && ignoreUrls.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }
        var sessionId = extractSessionId(request);
        if (sessionId == null) {
            return clearSessionAnd401(exchange, null);
        }
        // 阶段分离：鉴权阶段的失败在 authenticate 内部收敛为 401；
        // 转发阶段的失败不再触碰会话键，仅记录日志后原样抛出，
        // 交由 WebFlux 默认错误处理（未提交响应 → 500；已提交 → 框架丢弃并记日志）。
        return authenticate(exchange, sessionId, path)
                .flatMap(user -> forwardWithIdentity(exchange, chain, user))
                .onErrorResume(e -> {
                    log.error("网关转发异常（不影响会话）: path={}", path, e);
                    return Mono.error(e);
                });
    }

    /**
     * 鉴权阶段：loadValidUser + 重放兜底，所有失败路径在本方法内完成 401 响应写入
     * （fail-closed），返回 empty 表示 401 已写入、不再转发；异常绝不外溢到转发阶段。
     */
    private Mono<LoginUser> authenticate(ServerWebExchange exchange, String sessionId, String path) {
        return loadValidUser(exchange, sessionId)
                .switchIfEmpty(Mono.defer(() -> handleMissedRotation(exchange, sessionId)))
                .onErrorResume(ReplayRejectedException.class, _ -> {
                    log.warn("旧会话重放被拒，整族吊销: sessionId={}", sessionId);
                    return clearSessionAnd401(exchange, sessionId).then(Mono.<LoginUser>empty());
                })
                .onErrorResume(e -> {
                    log.error("网关鉴权异常，fail-closed 返回 401: path={}", path, e);
                    return clearSessionAnd401(exchange, sessionId).then(Mono.<LoginUser>empty());
                });
    }

    private @Nullable String extractSessionId(ServerHttpRequest request) {
        var auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.length() <= SessionConstant.BEARER_PREFIX.length()
                || !auth.regionMatches(true, 0, SessionConstant.BEARER_PREFIX, 0,
                SessionConstant.BEARER_PREFIX.length())) {
            return null;
        }
        var sessionId = auth.substring(SessionConstant.BEARER_PREFIX.length());
        return StringUtils.hasText(sessionId) ? sessionId : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层1：墓碑检测 → 活跃窗口 → session → 到期轮换
    //  正常路径失败返回 Mono.empty()，由调用方先复查墓碑再统一兜底 401；
    //  墓碑路径拒绝抛 ReplayRejectedException，由顶层 onErrorResume 统一写 401
    // ═══════════════════════════════════════════════════════════════
    private Mono<LoginUser> loadValidUser(ServerWebExchange exchange, String sessionId) {
        // 第一步：墓碑检测。无墓碑 → empty → 走正常路径；有墓碑 → handleReplay 终局
        return redisTemplate.opsForValue().get(SessionConstant.rotated(sessionId))
                .filter(StringUtils::hasText)
                .flatMap(familyId -> handleReplay(exchange, sessionId, familyId))
                .switchIfEmpty(Mono.defer(() -> loadActiveSession(exchange, sessionId)));
    }

    /**
     * 失败兜底前的墓碑复查：正常路径返回 empty 有两种可能——
     * 1) 会话确实无效（无墓碑）→ 401；
     * 2) 本请求在"读墓碑（未命中）"与"读活跃窗口（已被轮换赢家删除）"之间
     * 恰好撞上轮换完成 → 复查墓碑命中则改走重放路径自愈。
     * 轮换赢家保证"先写墓碑、再删旧键"，因此该窗口内墓碑必然已存在。
     */
    private Mono<LoginUser> handleMissedRotation(ServerWebExchange exchange, String sessionId) {
        return redisTemplate.opsForValue().get(SessionConstant.rotated(sessionId))
                .filter(StringUtils::hasText)
                .flatMap(familyId -> handleReplay(exchange, sessionId, familyId))
                .switchIfEmpty(Mono.defer(() -> reject401(exchange, sessionId)));
    }

    /**
     * 旧 sid 重放判定（无 Lua）。墓碑存在时本方法必须给出终局结果：
     * 放行（LoginUser）或拒绝（ReplayRejectedException），绝不返回 empty——
     * 否则外层 switchIfEmpty 会把"拒绝"当作"无墓碑"回退到正常路径，吊销被绕过。
     * <p>
     * 活跃窗口检查（refreshActiveWindow）返回布尔值并以 flatMap 串联；
     * 切勿改写为 then()——Reactor 的 then 不拦截空流，会静默绕过闸门（历史缺陷）。
     * <p>
     * 三种终局：
     * 1) 家族指针 == oldSid：轮换在"墓碑 → 家族指针"之间崩溃，指针未推进，
     * 活跃窗口存活则回滚墓碑自愈，否则整族吊销；
     * 2) prevSid == oldSid：轮换已完成但客户端未收到新 sid，活跃窗口存活则
     * 宽限补发当前 sid（窗口内计数限次，容忍并发突发），超限整族吊销；
     * 3) 其余（指针缺失 / prevSid 不匹配 / 会话缺失 / 休眠失效）：整族吊销 + 拒绝。
     */
    private Mono<LoginUser> handleReplay(ServerWebExchange exchange, String oldSid, String familyId) {
        return redisTemplate.opsForValue().get(SessionConstant.family(familyId))
                .filter(StringUtils::hasText)
                .flatMap(currentSid -> {
                    if (currentSid.equals(oldSid)) {
                        return rollbackTombstone(familyId, oldSid);
                    }
                    return graceReissue(exchange, familyId, oldSid, currentSid);
                })
                .switchIfEmpty(Mono.defer(() -> killFamily(familyId, null).then(reject())));
    }

    /**
     * 终局1：轮换在"墓碑 → 家族指针"之间崩溃 → 活跃窗口存活则回滚墓碑自愈。
     * 活跃窗口失效（休眠超 2 天）或会话缺失 → 整族吊销 + 拒绝。
     */
    private Mono<LoginUser> rollbackTombstone(String familyId, String oldSid) {
        return refreshActiveWindow(oldSid)
                .flatMap(active -> {
                    if (!Boolean.TRUE.equals(active)) {
                        log.warn("休眠旧会话触发回滚，整族吊销: familyId={}", familyId);
                        return killFamily(familyId, oldSid).then(reject());
                    }
                    log.warn("轮换中断自愈，回滚墓碑: oldSid={}", oldSid);
                    return redisTemplate.delete(SessionConstant.rotated(oldSid))
                            .then(loadSessionInfo(oldSid))
                            .map(SessionInfo::loginUser);
                })
                .switchIfEmpty(Mono.defer(() -> killFamily(familyId, oldSid).then(reject())));
    }

    /**
     * 终局2：轮换已完成（prevSid 匹配）→ 宽限补发。
     * 当前 sid 活跃窗口必须存活；prevSid 不匹配或会话缺失 → 整族吊销 + 拒绝。
     */
    private Mono<LoginUser> graceReissue(ServerWebExchange exchange, String familyId,
                                         String oldSid, String currentSid) {
        return refreshActiveWindow(currentSid)
                .flatMap(active -> {
                    if (!Boolean.TRUE.equals(active)) {
                        log.warn("休眠会话重放，整族吊销: familyId={}", familyId);
                        return killFamily(familyId, currentSid).then(reject());
                    }
                    return loadSessionInfo(currentSid)
                            .flatMap(info -> {
                                if (!info.vouchesFor(oldSid)) {
                                    // 超出重发窗口：只拒绝这个陈旧 sid，不整族吊销。
                                    // 陈旧令牌对攻击者价值最低，株连当前合法持有者得不偿失
                                    log.warn("旧 sid 超出重发窗口({}代)，仅拒绝: oldSid={}",
                                            SessionConstant.REISSUE_WINDOW, oldSid);
                                    return reject();
                                }
                                return grantGrace(exchange, familyId, oldSid, currentSid, info);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> killFamily(familyId, currentSid).then(reject())));
    }

    /**
     * 宽限计数（无 Lua）：INCR 原子计数，窗口内最多 {@link #GRACE_MAX_GRANTS} 次补发。
     * <p>
     * 一次性 SETNX 策略对"轮换瞬间的并发 / 多标签页突发"过于激进：第 2 个滞后请求
     * 就会触发整族吊销，连带删除刚下发的新 sid。计数窗口容忍突发；持续超限视为盗用。
     * 首次计数时设置窗口 TTL（后续不续期，避免滑动窗口被持续利用）。
     */
    private Mono<LoginUser> grantGrace(ServerWebExchange exchange, String familyId,
                                       String oldSid, String currentSid, SessionInfo info) {
        var graceKey = SessionConstant.grace(currentSid, oldSid);
        return redisTemplate.opsForValue().increment(graceKey)
                .switchIfEmpty(Mono.defer(() -> killFamily(familyId, currentSid).then(reject())))
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(graceKey, SessionConstant.ACTIVE_TTL).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > GRACE_MAX_GRANTS) {
                        log.warn("宽限次数超限({}/{}), 判定为持续重放: oldSid={}",
                                count, GRACE_MAX_GRANTS, oldSid);
                        return killFamily(familyId, currentSid).then(reject());
                    }
                    exchange.getResponse().getHeaders().set(SessionConstant.NEW_SESSION_HEADER, currentSid);
                    log.warn("旧会话宽限补发({}/{}): oldSid={} -> currentSid={}",
                            count, GRACE_MAX_GRANTS, oldSid, currentSid);
                    return Mono.just(info.loginUser());
                });
    }

    /**
     * 整族吊销：删当前 session、活跃窗口、家族指针。
     * 家族内其他 sid 因指针失效，无法再通过轮换/宽限路径续命。
     * currentSid 未知时（指针缺失）只删家族指针。
     */
    private Mono<Void> killFamily(String familyId, @Nullable String currentSid) {
        log.warn("检测到旧会话重放，整族吊销: familyId={}", familyId);
        return StringUtils.hasText(currentSid)
                ? redisTemplate.delete(
                SessionConstant.session(currentSid),
                SessionConstant.active(currentSid),
                SessionConstant.family(familyId)).then()
                : redisTemplate.delete(SessionConstant.family(familyId)).then();
    }

    /**
     * 活跃窗口检查 + 续期：session:last:{sid} 不存在 → false（休眠超 2 天），
     * 存在则续期并返回 true。统一以布尔判定供调用方 flatMap 控制后续分支——
     * 切勿改用 then() 串联（Reactor 的 then 对空信号不拦截，会绕过闸门）。
     */
    private Mono<Boolean> refreshActiveWindow(String sid) {
        return redisTemplate.opsForValue().get(SessionConstant.active(sid))
                .filter(StringUtils::hasText)
                .flatMap(_ -> redisTemplate.opsForValue()
                        .set(SessionConstant.active(sid), SessionConstant.ACTIVE_VALUE,
                                SessionConstant.ACTIVE_TTL))
                .defaultIfEmpty(false);
    }

    /**
     * 正常路径：活跃窗口（2 天不活跃即失效）→ 读取 session → rotateAt 到期则轮换
     */
    private Mono<LoginUser> loadActiveSession(ServerWebExchange exchange, String sessionId) {
        return refreshActiveWindow(sessionId)
                .flatMap(active -> Boolean.TRUE.equals(active)
                        ? loadSessionInfo(sessionId).flatMap(info -> maybeRotate(exchange, sessionId, info))
                        : Mono.empty());
    }

    private Mono<SessionInfo> loadSessionInfo(String sessionId) {
        return redisTemplate.opsForValue().get(SessionConstant.session(sessionId))
                .filter(StringUtils::hasText)
                .flatMap(json -> Mono.fromCallable(
                                () -> objectMapper.readValue(json, SessionInfo.class))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> Mono.empty()));
    }

    /**
     * 轮换（无 Lua）：先建新键，再 SETNX 墓碑抢占，赢家才推进家族指针并删旧。
     * 家族指针只由墓碑赢家写入 → 并发轮换时指针不会悬空；输家只清理自己的孤儿新键。
     * 输家客户端仍持旧 sid，下次请求走重放宽限路径补发新 sid，自愈。
     * <p>
     * 崩溃窗口：
     * - 墓碑之前崩溃：孤儿新键自然过期，旧世界完整，下次请求重试轮换；
     * - 墓碑 → 指针之间崩溃：重放触发 handleReplay 回滚墓碑（oldSid 仍是当前会话）；
     * - 指针 → 删旧之间崩溃：重放走宽限路径补发新 sid。
     */
    private Mono<LoginUser> maybeRotate(ServerWebExchange exchange, String sessionId, SessionInfo info) {
        if (System.currentTimeMillis() < info.rotateAt()) {
            return Mono.just(info.loginUser());
        }
        var newSid = UUID.randomUUID().toString();
        var next = info.rotate(sessionId);
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(next))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(SessionConstant.session(newSid), json, SessionConstant.SESSION_TTL)
                        .then(redisTemplate.opsForValue()
                                .set(SessionConstant.active(newSid), SessionConstant.ACTIVE_VALUE,
                                        SessionConstant.ACTIVE_TTL))
                        .then(redisTemplate.opsForValue()
                                .setIfAbsent(SessionConstant.rotated(sessionId),
                                        info.familyId(), SessionConstant.SESSION_TTL))
                        .flatMap(acquired -> {
                            if (!Boolean.TRUE.equals(acquired)) {
                                // 并发已轮换：清理自己的孤儿新键后放行，不写 header，
                                // 客户端仍持旧 sid，下次请求经宽限路径拿到赢家的新 sid
                                return redisTemplate.delete(
                                                SessionConstant.session(newSid),
                                                SessionConstant.active(newSid))
                                        .then(Mono.just(info.loginUser()));
                            }
                            // 赢家：先推进家族指针，再删旧键 —— 指针永不悬空
                            return redisTemplate.opsForValue()
                                    .set(SessionConstant.family(info.familyId()), newSid,
                                            SessionConstant.SESSION_TTL)
                                    .then(redisTemplate.delete(
                                            SessionConstant.session(sessionId),
                                            SessionConstant.active(sessionId)))
                                    .then(Mono.fromRunnable(() -> exchange.getResponse()
                                            .getHeaders().set(SessionConstant.NEW_SESSION_HEADER, newSid)))
                                    .thenReturn(info.loginUser());
                        }));
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层2：注入身份 + 构建请求 + 转发
    // ═══════════════════════════════════════════════════════════════
    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange,
                                           WebFilterChain chain,
                                           LoginUser loginUser) {
        exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
        var mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", loginUser.getUserId())
                .build();
        var finalRequest = mutatedRequest.getMethod() == HttpMethod.GET
                ? handleGetRequest(mutatedRequest, loginUser.getUserId())
                : new BodyRewriteDecorator(mutatedRequest, exchange);
        return chain.filter(exchange.mutate().request(finalRequest).build());
    }

    /**
     * GET 请求：userId 拼入 Query String（replaceQueryParam 自动去重与编码）
     */
    private ServerHttpRequest handleGetRequest(ServerHttpRequest request, String userId) {
        var uri = UriComponentsBuilder.fromUri(request.getURI())
                .replaceQueryParam("userId", userId)
                .build(true).toUri();
        return request.mutate().uri(uri).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  401 + 清除会话
    // ═══════════════════════════════════════════════════════════════
    private Mono<Void> clearSessionAnd401(ServerWebExchange exchange, @Nullable String sessionId) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl(CacheControl.noStore().getHeaderValue());
        var buffer = response.bufferFactory().wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        var cleanup = StringUtils.hasText(sessionId)
                ? redisTemplate.delete(SessionConstant.session(sessionId),
                        SessionConstant.active(sessionId))
                .onErrorResume(e -> {
                    log.warn("⚠️ 鉴权失败清理会话键失败（Redis 不可用）: sid={}", sessionId, e);
                    return Mono.empty();
                })
                .then()
                : Mono.empty();
        return cleanup.then(response.writeWith(Mono.just(buffer)));
    }

    private Mono<LoginUser> reject401(ServerWebExchange exchange, String sessionId) {
        return clearSessionAnd401(exchange, sessionId).then(Mono.empty());
    }

    /**
     * 墓碑路径的终局拒绝信号：以异常穿透 switchIfEmpty，确保绝不回退到正常路径。
     * 由顶层 filter 的 onErrorResume 统一写 401 并清理旧 sid 残留键。
     */
    private static final class ReplayRejectedException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 4512572870097915892L;

        ReplayRejectedException() {
            super("旧会话重放被拒", null, false, false);
        }
    }

    private static <T> Mono<T> reject() {
        return Mono.error(new ReplayRejectedException());
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════════════════════════
    @Override
    public int getOrder() {
        return -1000;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Body 重写装饰器
    //  JSON 预检 + Content-Type 过滤 + 有上限缓冲(413) + 读超时(408) + 空 Body 降级 + 单次缓冲
    // ═══════════════════════════════════════════════════════════════
    private class BodyRewriteDecorator extends ServerHttpRequestDecorator {

        private final ServerWebExchange exchange;
        private final AtomicReference<@Nullable Mono<byte[]>> rewrittenBody = new AtomicReference<>();

        BodyRewriteDecorator(ServerHttpRequest delegate, ServerWebExchange exchange) {
            super(delegate);
            this.exchange = exchange;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            var loginUser = exchange.getAttribute(ATTR_LOGIN_USER);
            if (loginUser == null) {
                return super.getBody();
            }
            // 预检：非 JSON 直接透传，不做任何缓冲
            var contentType = getHeaders().getContentType();
            if (!isJsonContentType(contentType)) {
                return super.getBody();
            }
            // 预检：Content-Length 超过阈值直接透传（读之前判定，防 OOM）
            if (super.getHeaders().getContentLength() > MAX_BODY_BUFFER_SIZE) {
                log.warn("Body size {} exceeds limit {}, skip loginUser injection for {}",
                        getHeaders().getContentLength(), MAX_BODY_BUFFER_SIZE, getURI().getPath());
                return super.getBody();
            }
            // 只缓冲一次：getBody() 可能被多次调用，重复 join 会读到空 body。
            // AtomicReference 懒加载保证并发安全；竞态中未中标的 Mono 不会被订阅，无副作用
            var cached = rewrittenBody.updateAndGet(current ->
                    current != null ? current : rewriteBody(loginUser).cache());
            // 静态收口：运算符恒返回非空，此分支仅用于空值分析；兜底返回原始 body 不破坏语义
            if (cached == null) {
                return super.getBody();
            }
            return cached.map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes)).flux();
        }

        private Mono<byte[]> rewriteBody(Object loginUser) {
            // 有上限 join：chunked/无 Content-Length 时也不会无限缓冲，
            // 超限抛 DataBufferLimitException（Spring 会释放已收集的 buffer）。
            // 带 Content-Length 的超大 body 已在 getBody 预检处透传，根本不会走到这里
            return DataBufferUtils.join(super.getBody(), MAX_BODY_BUFFER_SIZE)
                    .timeout(BODY_READ_TIMEOUT)
                    .map(BodyRewriteDecorator::drainBuffer)
                    .flatMap(bytes -> Mono.fromCallable(() -> injectLoginUser(bytes, loginUser))
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorReturn(bytes))
                    // 空 Body 降级：构造仅含 loginUser 的 JSON
                    .switchIfEmpty(Mono.fromCallable(() -> {
                                ObjectNode emptyJson = objectMapper.createObjectNode();
                                emptyJson.set(LOGIN_USER, objectMapper.valueToTree(loginUser));
                                return objectMapper.writeValueAsBytes(emptyJson);
                            })
                            .subscribeOn(Schedulers.boundedElastic()))
                    .onErrorMap(DataBufferLimitException.class,
                            e -> new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                                    "请求体超过 " + MAX_BODY_BUFFER_SIZE + " 字节限制", e))
                    .onErrorMap(TimeoutException.class,
                            e -> new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT,
                                    "请求体读取超时", e));
        }

        private static byte[] drainBuffer(DataBuffer dataBuffer) {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return bytes;
        }

        private byte[] injectLoginUser(byte[] bytes, Object loginUser) {
            JsonNode jsonNode = objectMapper.readTree(bytes);
            if (jsonNode.isObject()) {
                ((ObjectNode) jsonNode).set(LOGIN_USER, objectMapper.valueToTree(loginUser));
            }
            return objectMapper.writeValueAsBytes(jsonNode);
        }

        @Override
        public HttpHeaders getHeaders() {
            var headers = new HttpHeaders();
            headers.putAll(super.getHeaders());
            // 仅当 body 确实会被重写（JSON 且未超限透传）时才必须移除旧 Content-Length；
            // 透传分支保留原始长度头，避免无谓地退化为 chunked
            var contentType = super.getHeaders().getContentType();
            if (isJsonContentType(contentType)
                    && super.getHeaders().getContentLength() <= MAX_BODY_BUFFER_SIZE) {
                headers.remove(HttpHeaders.CONTENT_LENGTH);
            }
            return headers;
        }

        private boolean isJsonContentType(@Nullable MediaType contentType) {
            return contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
        }
    }
}