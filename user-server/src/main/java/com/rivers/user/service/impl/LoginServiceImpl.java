package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.ConfirmQrCodeReq;
import com.rivers.proto.LoginReq;
import com.rivers.proto.QrCodeRes;
import com.rivers.proto.ScanQrCodeReq;
import com.rivers.user.config.QrCodeWebSocketHandler;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.service.ILoginService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * SSO 登录服务 — Cookie 会话模式（无 refreshToken）
 * <p>
 * session 固定 30 天 TTL 不续期，JWT 过期由网关自动刷新。
 * 返回值仅含 userId / username，不含任何 token。
 */
@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String TOKEN_PREFIX = "token:";
    private static final String QR_PREFIX = "qr:code:";
    private static final String QR_STATUS_PREFIX = "qr:status:";
    private static final String QR_SESSION_PREFIX = "qr:session:";
    private static final String SESSION_PREFIX = "session:";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final long QR_CODE_EXPIRE_SECONDS = 300L;
    private static final long REFRESH_EXPIRE_DAYS = 30L;
    private static final long FAIL_LIMIT = 5L;
    private static final long FAIL_WINDOW_HOURS = 1L;
    static final String NO_USER = "用户不存在";
    static final String SCANNED = "SCANNED";
    static final String COOKIE_SESSION = "SESSION_ID";
    private static final String SESSION_KEY_TOKEN = "accessToken";
    private static final Duration SESSION_TTL = Duration.ofDays(REFRESH_EXPIRE_DAYS);
    private static final Duration QR_TTL = Duration.ofSeconds(QR_CODE_EXPIRE_SECONDS);
    private final TimerUserMapper timerUserMapper;
    private final QrCodeWebSocketHandler qrCodeWebSocketHandler;
    private final StringRedisTemplate stringRedisTemplate;

    public LoginServiceImpl(TimerUserMapper timerUserMapper,
                            QrCodeWebSocketHandler qrCodeWebSocketHandler,
                            StringRedisTemplate stringRedisTemplate) {
        this.timerUserMapper = timerUserMapper;
        this.qrCodeWebSocketHandler = qrCodeWebSocketHandler;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ═══════════════════════════════════════════════════════════════
    //  密码 / 自动登录
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResultVO<Void> login(LoginReq req, ServerHttpResponse response) {
        var username = req.getUsername();
        var password = req.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultVO.fail("登录失败");
        }
        var basicToken = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return doLogin(BASIC_AUTH_PREFIX + basicToken, response);
    }

    @Override
    public ResultVO<Void> autoLogin(String authHeader, ServerHttpResponse response) {
        return doLogin(authHeader, response);
    }

    // ═══════════════════════════════════════════════════════════════
    //  二维码登录
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResultVO<QrCodeRes> generateQrCode() {
        var qrCodeId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue()
                .set(QR_STATUS_PREFIX + qrCodeId, "WAIT_SCAN", QR_TTL);
        log.info("生成二维码: {}", qrCodeId);
        return ResultVO.ok(QrCodeRes.newBuilder()
                .setQrCodeId(qrCodeId)
                .setQrCodeContent(qrCodeId)
                .setExpireTime(QR_CODE_EXPIRE_SECONDS)
                .build());
    }

    @Override
    public ResultVO<Void> scanQrCode(ScanQrCodeReq req) {
        var qrCodeId = req.getQrCodeId();
        var userId = req.getLoginUser().getUserId();
        var statusKey = QR_STATUS_PREFIX + qrCodeId;
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(statusKey))
                .filter(s -> !s.isBlank())
                .map(currentStatus ->
                        switch (currentStatus) {
                            case "WAIT_SCAN" -> processScan(qrCodeId, userId, statusKey);
                            default -> ResultVO.<Void>fail("二维码状态异常");
                        })
                .orElseGet(() -> ResultVO.fail("二维码已过期"));
    }

    private ResultVO<Void> processScan(String qrCodeId, String userId, String statusKey) {
        var user = findUserById(userId);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        stringRedisTemplate.opsForValue().set(statusKey, SCANNED, QR_TTL);
        stringRedisTemplate.opsForValue()
                .set(QR_PREFIX + qrCodeId + "user", userId, QR_TTL);

        var scanData = new HashMap<String, String>();
        scanData.put("userId", userId);
        scanData.put("username", user.getUsername());
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, SCANNED, scanData);
        log.info("二维码已扫描: {}, 用户: {}", qrCodeId, userId);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> confirmQrCode(ConfirmQrCodeReq req) {
        var qrCodeId = req.getQrCodeId();
        var userId = req.getUserId();
        var statusKey = QR_STATUS_PREFIX + qrCodeId;
        var currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!SCANNED.equals(currentStatus)) {
            return ResultVO.fail("请先扫描二维码");
        }
        var storedUserId = stringRedisTemplate.opsForValue()
                .get(QR_PREFIX + qrCodeId + "user");
        if (!userId.equals(storedUserId)) {
            return ResultVO.fail("用户信息不匹配");
        }
        var user = findUserById(userId);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        // 建 JWT + session
        var loginUser = buildLoginUser(user);
        var sessionId = buildSession(loginUser);

        // 临时映射，供前端 claim 接口领取（5 分钟有效）
        var claimData = Map.of(
                "sessionId", sessionId,
                "userId", loginUser.getUserId(),
                "username", loginUser.getUsername()
        );
        stringRedisTemplate.opsForValue()
                .set(QR_SESSION_PREFIX + qrCodeId,
                        JSONUtil.toJsonStr(claimData), Duration.ofMinutes(5));
        // WebSocket 只发信号，不含敏感数据
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED",
                Map.of("qrCodeId", qrCodeId, "status", "CONFIRMED"));
        stringRedisTemplate.delete(QR_PREFIX + qrCodeId + "user");
        stringRedisTemplate.delete(statusKey);
        log.info("二维码已确认: {}, 用户: {}, sessionId={}", qrCodeId, userId, sessionId);
        return ResultVO.ok();
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：登录核心
    // ═══════════════════════════════════════════════════════════════

    private ResultVO<Void> doLogin(String authHeader, ServerHttpResponse response) {
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
            return ResultVO.fail(401, "请先登录");
        }
        var credentials = parseBasicAuth(authHeader);
        if (credentials.length < 2) {
            return ResultVO.fail(401, "凭证格式错误");
        }
        var username = credentials[0];
        var password = credentials[1];
        var user = findUserById(username);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        if (!user.getPassword().equals(password)) {
            return handleFailCount(username);
        }
        stringRedisTemplate.delete(FAIL_PREFIX + username);
        var loginUser = buildLoginUser(user);
        var sessionId = buildSession(loginUser);
        setSessionCookie(response, sessionId);
        log.info("登录成功: sessionId={}, userId={}", sessionId, user.getUserId());
        return ResultVO.ok();
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：Session / JWT
    // ═══════════════════════════════════════════════════════════════

    private String buildSession(LoginUser loginUser) {
        var key = UUID.randomUUID().toString();
        var token = JwtUtil.createJwt(loginUser, key);
        var sessionId = UUID.randomUUID().toString();
        var session = Map.of(SESSION_KEY_TOKEN, token);    // 单字段，Map.of 足够
        stringRedisTemplate.opsForValue()
                .set(TOKEN_PREFIX + key, token, SESSION_TTL);
        stringRedisTemplate.opsForValue()
                .set(SESSION_PREFIX + sessionId,
                        JSONUtil.toJsonStr(session), SESSION_TTL);
        return sessionId;
    }

    private void setSessionCookie(ServerHttpResponse response, String sessionId) {
        response.addCookie(ResponseCookie.from(COOKIE_SESSION, sessionId)
                .httpOnly(true)
//                .secure(true)
                .path("/")
                .maxAge(SESSION_TTL)
                .sameSite("Strict")
                .build());
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：辅助
    // ═══════════════════════════════════════════════════════════════

    private LoginUser buildLoginUser(TimerUser user) {
        var loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        return loginUser;
    }

    private String[] parseBasicAuth(String authHeader) {
        var basicToken = CharSequenceUtil.subAfter(authHeader, BASIC_AUTH_PREFIX, false);
        if (StringUtils.isBlank(basicToken)) {
            return new String[0];
        }
        var decoded = Base64.getDecoder().decode(basicToken);
        var parts = new String(decoded, StandardCharsets.UTF_8).split(":", 2);
        if (parts.length < 2
                || StringUtils.isBlank(parts[0])
                || StringUtils.isBlank(parts[1])) {
            return new String[0];
        }
        return parts;
    }

    private TimerUser findUserById(String userId) {
        return timerUserMapper.selectOne(Wrappers.<TimerUser>lambdaQuery()
                .eq(TimerUser::getUserId, userId));
    }

    private ResultVO<Void> handleFailCount(String username) {
        var failKey = FAIL_PREFIX + username;
        var fails = stringRedisTemplate.opsForValue().increment(failKey);
        stringRedisTemplate.expire(failKey, Duration.ofHours(FAIL_WINDOW_HOURS));
        if (fails != null && fails > FAIL_LIMIT) {
            return ResultVO.fail("请求过于频繁");
        }
        return ResultVO.fail("登录失败");
    }
}