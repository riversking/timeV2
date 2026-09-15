package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.constant.SessionConstant;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.entity.SessionInfo;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.config.QrCodeWebSocketHandler;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.service.ILoginService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

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
    private static final Duration QR_TTL = Duration.ofSeconds(QR_CODE_EXPIRE_SECONDS);
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";

    private final TimerUserMapper timerUserMapper;
    private final QrCodeWebSocketHandler qrCodeWebSocketHandler;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public LoginServiceImpl(TimerUserMapper timerUserMapper,
                            QrCodeWebSocketHandler qrCodeWebSocketHandler,
                            StringRedisTemplate stringRedisTemplate,
                            ObjectMapper objectMapper) {
        this.timerUserMapper = timerUserMapper;
        this.qrCodeWebSocketHandler = qrCodeWebSocketHandler;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  密码 / 自动登录
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResultVO<AutoLoginRes> login(LoginReq req) {
        var username = req.getUsername();
        var password = req.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultVO.fail(BAD_CREDENTIALS);
        }
        var basicToken = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return doLogin(BASIC_AUTH_PREFIX + basicToken);
    }

    @Override
    public ResultVO<AutoLoginRes> autoLogin(String authHeader) {
        return doLogin(authHeader);
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
        // DEL 作为抢注：返回 true 才继续，防并发重复扫码（Redis DEL 本身原子）
        var claimed = Boolean.TRUE.equals(stringRedisTemplate.delete(statusKey));
        if (!claimed) {
            return ResultVO.fail("二维码已被扫描");
        }
        stringRedisTemplate.opsForValue().set(statusKey, SCANNED, QR_TTL);
        stringRedisTemplate.opsForValue()
                .set(QR_USER_PREFIX + qrCodeId, userId, QR_TTL);

        var scanData = new HashMap<String, String>();
        scanData.put(USER_ID, userId);
        scanData.put(USERNAME, user.getUsername());
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
                .get(QR_USER_PREFIX + qrCodeId);
        if (!userId.equals(storedUserId)) {
            return ResultVO.fail("用户信息不匹配");
        }
        var user = findUserById(userId);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        // 建 session（纯 Bearer 模式，无 JWT）
        var loginUser = buildLoginUser(user);
        var sessionId = buildSession(loginUser);

        // 临时映射，供前端 claim 接口领取（5 分钟有效）
        var claimData = Map.of(
                "sessionId", sessionId,
                USER_ID, loginUser.getUserId(),
                USERNAME, loginUser.getUsername()
        );
        stringRedisTemplate.opsForValue()
                .set(QR_SESSION_PREFIX + qrCodeId,
                        JSONUtil.toJsonStr(claimData), Duration.ofMinutes(5));
        // WebSocket 只发信号，不含敏感数据
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED",
                Map.of("qrCodeId", qrCodeId, "status", "CONFIRMED"));
        stringRedisTemplate.delete(QR_USER_PREFIX + qrCodeId);
        stringRedisTemplate.delete(statusKey);
        log.info("二维码已确认: {}, 用户: {}", qrCodeId, userId);
        return ResultVO.ok();
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有：登录核心
    // ═══════════════════════════════════════════════════════════════

    private ResultVO<AutoLoginRes> doLogin(String authHeader) {
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
            return ResultVO.fail(BAD_CREDENTIALS);
        }
        if (!user.getPassword().equals(password)) {
            return handleFailCount(username);
        }
        stringRedisTemplate.delete(FAIL_PREFIX + username);
        var loginUser = buildLoginUser(user);
        var sessionId = buildSession(loginUser);
        var autoLoginRes = AutoLoginRes.newBuilder()
                .setToken(sessionId)
                .build();
        log.info("登录成功: userId={}", user.getUserId());
        return ResultVO.ok(autoLoginRes);
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
    private String buildSession(LoginUser loginUser) {
        var sessionId = UUID.randomUUID().toString();
        var familyId = UUID.randomUUID().toString();
        var session = SessionInfo.create(loginUser.getUserId(), loginUser.getUsername(), familyId);
        var json = objectMapper.writeValueAsString(session);
        stringRedisTemplate.opsForValue()
                .set(SessionConstant.session(sessionId), json, SessionConstant.SESSION_TTL);
        stringRedisTemplate.opsForValue()
                .set(SessionConstant.family(familyId), sessionId, SessionConstant.SESSION_TTL);
        stringRedisTemplate.opsForValue()
                .set(SessionConstant.active(sessionId), SessionConstant.ACTIVE_VALUE,
                        SessionConstant.ACTIVE_TTL);
        return sessionId;
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

    private ResultVO<AutoLoginRes> handleFailCount(String username) {
        var failKey = FAIL_PREFIX + username;
        var fails = stringRedisTemplate.opsForValue().increment(failKey);
        // 仅首次失败设置窗口（INCR 原子；EXPIRE 非原子但崩溃残留概率极低，
        // 登录成功时会 delete 兜底）
        if (fails != null && fails == 1L) {
            stringRedisTemplate.expire(failKey, Duration.ofHours(FAIL_WINDOW_HOURS));
        }
        if (fails != null && fails >= FAIL_LIMIT) {
            return ResultVO.fail("请求过于频繁");
        }
        return ResultVO.fail(BAD_CREDENTIALS);
    }
}