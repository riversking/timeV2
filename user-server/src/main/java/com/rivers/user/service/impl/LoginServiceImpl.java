package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.rivers.core.constant.SessionConstant;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.entity.SessionInfo;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.config.QrCodeWebSocketHandler;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.repository.TimerUserRepository;
import com.rivers.user.service.ILoginService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * SSO 登录服务 — 纯 Session Bearer 模式（无 JWT / 无 token 键）
 * <p>
 * session:{sid} 固定 30 天绝对期限不续期；12h 轮换、宽限与盗用吊销由网关负责。
 * 返回值仅含 userId / username，不含任何 token。
 */
@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String QR_STATUS_PREFIX = "qr:status:";
    private static final String QR_USER_PREFIX = "qr:user:";
    private static final String QR_SESSION_PREFIX = "qr:session:";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final long QR_CODE_EXPIRE_SECONDS = 300L;
    private static final long FAIL_LIMIT = 5L;
    private static final long FAIL_WINDOW_HOURS = 1L;
    private static final String NO_USER = "用户不存在";
    private static final String BAD_CREDENTIALS = "用户名或密码错误";
    private static final String SCANNED = "SCANNED";
    private static final String WAIT_SCAN = "WAIT_SCAN";
    private static final Duration QR_TTL = Duration.ofSeconds(QR_CODE_EXPIRE_SECONDS);
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";
    private static final Integer NOT_DELETED = 0;

    private final TimerUserRepository timerUserRepository;
    private final QrCodeWebSocketHandler qrCodeWebSocketHandler;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    public LoginServiceImpl(TimerUserRepository timerUserRepository,
                            QrCodeWebSocketHandler qrCodeWebSocketHandler,
                            ReactiveStringRedisTemplate reactiveRedisTemplate,
                            ObjectMapper objectMapper) {
        this.timerUserRepository = timerUserRepository;
        this.qrCodeWebSocketHandler = qrCodeWebSocketHandler;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  密码 / 自动登录
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Mono<ResultVO<AutoLoginRes>> login(LoginReq req) {
        String username = req.getUsername();
        String password = req.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Mono.just(ResultVO.fail(BAD_CREDENTIALS));
        }
        String basicToken = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return doLogin(BASIC_AUTH_PREFIX + basicToken);
    }

    @Override
    public Mono<ResultVO<AutoLoginRes>> autoLogin(String authHeader) {
        return doLogin(authHeader);
    }

    // ═══════════════════════════════════════════════════════════════
    //  二维码登录
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Mono<ResultVO<QrCodeRes>> generateQrCode() {
        String qrCodeId = UUID.randomUUID().toString();
        return reactiveRedisTemplate.opsForValue()
                .set(QR_STATUS_PREFIX + qrCodeId, WAIT_SCAN, QR_TTL)
                .doOnNext(ok -> log.info("生成二维码: {}", qrCodeId))
                .map(ok -> ResultVO.ok(QrCodeRes.newBuilder()
                        .setQrCodeId(qrCodeId)
                        .setQrCodeContent(qrCodeId)
                        .setExpireTime(QR_CODE_EXPIRE_SECONDS)
                        .build()));
    }

    @Override
    public Mono<ResultVO<Void>> scanQrCode(ScanQrCodeReq req) {
        String qrCodeId = req.getQrCodeId();
        String userId = req.getLoginUser().getUserId();
        String statusKey = QR_STATUS_PREFIX + qrCodeId;
        return reactiveRedisTemplate.opsForValue().get(statusKey)
                .flatMap(currentStatus -> {
                    if (StringUtils.isBlank(currentStatus)) {
                        return Mono.just(ResultVO.<Void>fail("二维码已过期"));
                    }
                    if (!WAIT_SCAN.equals(currentStatus)) {
                        return Mono.just(ResultVO.<Void>fail("二维码状态异常"));
                    }
                    return processScan(qrCodeId, userId, statusKey);
                })
                .switchIfEmpty(Mono.just(ResultVO.<Void>fail("二维码已过期")));
    }

    private Mono<ResultVO<Void>> processScan(String qrCodeId, String userId, String statusKey) {
        return findUserById(userId)
                .flatMap(user -> reactiveRedisTemplate.delete(statusKey)
                        .flatMap(claimed -> {
                            if (!Long.valueOf(1L).equals(claimed)) {
                                return Mono.just(ResultVO.<Void>fail("二维码已被扫描"));
                            }
                            return reactiveRedisTemplate.opsForValue().set(statusKey, SCANNED, QR_TTL)
                                    .then(reactiveRedisTemplate.opsForValue()
                                            .set(QR_USER_PREFIX + qrCodeId, userId, QR_TTL))
                                    .doOnSuccess(ok -> {
                                        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, SCANNED,
                                                Map.of(USER_ID, userId, USERNAME, user.getUsername()));
                                        log.info("二维码已扫描: {}, 用户: {}", qrCodeId, userId);
                                    })
                                    .thenReturn(ResultVO.<Void>ok());
                        }))
                .switchIfEmpty(Mono.just(ResultVO.<Void>fail(NO_USER)));
    }

    @Override
    public Mono<ResultVO<Void>> confirmQrCode(ConfirmQrCodeReq req) {
        String qrCodeId = req.getQrCodeId();
        String userId = req.getUserId();
        String statusKey = QR_STATUS_PREFIX + qrCodeId;
        String userKey = QR_USER_PREFIX + qrCodeId;
        return reactiveRedisTemplate.opsForValue().get(statusKey)
                .flatMap(currentStatus -> {
                    if (StringUtils.isBlank(currentStatus)) {
                        return Mono.just(ResultVO.<Void>fail("二维码已过期"));
                    }
                    if (!SCANNED.equals(currentStatus)) {
                        return Mono.just(ResultVO.<Void>fail("请先扫描二维码"));
                    }
                    return reactiveRedisTemplate.opsForValue().get(userKey)
                            .flatMap(storedUserId -> {
                                if (!userId.equals(storedUserId)) {
                                    return Mono.just(ResultVO.<Void>fail("用户信息不匹配"));
                                }
                                return completeQrLogin(qrCodeId, userId, statusKey, userKey);
                            })
                            .switchIfEmpty(Mono.just(ResultVO.<Void>fail("二维码已过期")));
                })
                .switchIfEmpty(Mono.just(ResultVO.<Void>fail("二维码已过期")));
    }

    private Mono<ResultVO<Void>> completeQrLogin(String qrCodeId, String userId,
                                                 String statusKey, String userKey) {
        return findUserById(userId)
                .flatMap(user -> {
                    LoginUser loginUser = buildLoginUser(user);
                    return buildSession(loginUser)
                            .flatMap(sessionId -> {
                                Map<String, String> claimData = Map.of(
                                        "sessionId", sessionId,
                                        USER_ID, loginUser.getUserId(),
                                        USERNAME, loginUser.getUsername());
                                return reactiveRedisTemplate.opsForValue()
                                        .set(QR_SESSION_PREFIX + qrCodeId,
                                                JSONUtil.toJsonStr(claimData), Duration.ofMinutes(5))
                                        .doOnSuccess(ok -> {
                                            // WebSocket 只发信号，不含敏感数据
                                            qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED",
                                                    Map.of("qrCodeId", qrCodeId, "status", "CONFIRMED"));
                                            log.info("二维码已确认: {}, 用户: {}", qrCodeId, userId);
                                        })
                                        .then(reactiveRedisTemplate.delete(userKey))
                                        .then(reactiveRedisTemplate.delete(statusKey))
                                        .thenReturn(ResultVO.<Void>ok());
                            });
                })
                .switchIfEmpty(Mono.just(ResultVO.<Void>fail(NO_USER)));
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：登录核心
    // ═══════════════════════════════════════════════════════════════

    private Mono<ResultVO<AutoLoginRes>> doLogin(String authHeader) {
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
            return Mono.just(ResultVO.fail(401, "请先登录"));
        }
        String[] credentials = parseBasicAuth(authHeader);
        if (credentials.length < 2) {
            return Mono.just(ResultVO.fail(401, "凭证格式错误"));
        }
        String username = credentials[0];
        String password = credentials[1];
        return findUserById(username)
                .flatMap(user -> {
                    if (!user.getPassword().equals(password)) {
                        return handleFailCount(username);
                    }
                    return reactiveRedisTemplate.delete(FAIL_PREFIX + username)
                            .then(buildSession(buildLoginUser(user)))
                            .map(sessionId -> {
                                log.info("登录成功: userId={}", user.getUserId());
                                return ResultVO.ok(AutoLoginRes.newBuilder()
                                        .setToken(sessionId)
                                        .build());
                            });
                })
                .switchIfEmpty(Mono.just(ResultVO.<AutoLoginRes>fail(BAD_CREDENTIALS)));
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：Session
    // ═══════════════════════════════════════════════════════════════

    /**
     * session JSON 直接存用户信息（无 JWT）。
     * 配套写家族指针（session:family:{familyId}）与活跃窗口（session:last:{sid}），
     * 轮换/宽限/盗用吊销由网关基于这三个键完成。
     * 键名/结构契约见 {@link SessionConstant} / {@link SessionInfo}（rivers-core 统一定义）。
     */
    private Mono<String> buildSession(LoginUser loginUser) {
        String sessionId = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        SessionInfo session = SessionInfo.create(loginUser.getUserId(), loginUser.getUsername(), familyId);
        String json = objectMapper.writeValueAsString(session);
        return reactiveRedisTemplate.opsForValue()
                .set(SessionConstant.session(sessionId), json, SessionConstant.SESSION_TTL)
                .then(reactiveRedisTemplate.opsForValue()
                        .set(SessionConstant.family(familyId), sessionId, SessionConstant.SESSION_TTL))
                .then(reactiveRedisTemplate.opsForValue()
                        .set(SessionConstant.active(sessionId), SessionConstant.ACTIVE_VALUE,
                                SessionConstant.ACTIVE_TTL))
                .thenReturn(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：辅助
    // ═══════════════════════════════════════════════════════════════

    private LoginUser buildLoginUser(TimerUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        return loginUser;
    }

    private String[] parseBasicAuth(String authHeader) {
        String basicToken = CharSequenceUtil.subAfter(authHeader, BASIC_AUTH_PREFIX, false);
        if (StringUtils.isBlank(basicToken)) {
            return new String[0];
        }
        byte[] decoded = Base64.getDecoder().decode(basicToken);
        String[] parts = new String(decoded, StandardCharsets.UTF_8).split(":", 2);
        if (parts.length < 2
                || StringUtils.isBlank(parts[0])
                || StringUtils.isBlank(parts[1])) {
            return new String[0];
        }
        return parts;
    }

    private Mono<TimerUser> findUserById(String userId) {
        return timerUserRepository.findByUserIdAndIsDeleted(userId, NOT_DELETED);
    }

    private Mono<ResultVO<AutoLoginRes>> handleFailCount(String username) {
        String failKey = FAIL_PREFIX + username;
        return reactiveRedisTemplate.opsForValue().increment(failKey)
                .defaultIfEmpty(0L)
                .flatMap(fails -> {
                    // 仅首次失败设置窗口（INCR 原子；EXPIRE 非原子但崩溃残留概率极低，
                    // 登录成功时会 delete 兜底）
                    Mono<Boolean> expireMono = fails == 1L
                            ? reactiveRedisTemplate.expire(failKey, Duration.ofHours(FAIL_WINDOW_HOURS))
                            : Mono.empty();
                    if (fails >= FAIL_LIMIT) {
                        return expireMono.thenReturn(ResultVO.<AutoLoginRes>fail("请求过于频繁"));
                    }
                    return expireMono.thenReturn(ResultVO.<AutoLoginRes>fail(BAD_CREDENTIALS));
                });
    }
}
