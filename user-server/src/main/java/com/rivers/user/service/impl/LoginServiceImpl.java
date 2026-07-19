package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Maps;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
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

@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    private static final String TOKEN_PREFIX = "token:";
    private static final String REFRESH_PREFIX = "refresh:token:";
    private static final String FAIL_PREFIX = "login:fail:";
    private static final String QR_PREFIX = "qr:code:";
    private static final String QR_STATUS_PREFIX = "qr:status:";
    private static final String SESSION_PREFIX = "session:";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final long QR_CODE_EXPIRE_SECONDS = 300L;
    private static final long JWT_EXPIRE_MINUTES = 30L;
    private static final long REFRESH_EXPIRE_DAYS = 30L;
    private static final long FAIL_LIMIT = 5L;
    private static final long FAIL_WINDOW_HOURS = 1L;
    private static final String NO_USER = "用户不存在";
    private static final String SCANNED = "SCANNED";
    private static final String COOKIE_SESSION = "SESSION_ID";
    private static final String SESSION_KEY_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";
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


    @Override
    public ResultVO<Void> login(LoginReq loginReq, ServerHttpResponse response) {
        String username = loginReq.getUsername();
        String password = loginReq.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultVO.fail("登录失败");
        }
        String basicToken = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return doLogin(BASIC_AUTH_PREFIX + basicToken, response);
    }

    @Override
    public ResultVO<Void> autoLogin(String authHeader, ServerHttpResponse response) {
        return doLogin(authHeader, response);
    }

    @Override
    public ResultVO<Void> refresh(AutoLoginReq autoLoginReq,
                                  ServerHttpResponse response) {
        String refreshToken = autoLoginReq.getRefreshToken();
        if (StringUtils.isBlank(refreshToken)) {
            return ResultVO.fail(401, "请先登录");
        }
        String userId = stringRedisTemplate.opsForValue()
                .get(REFRESH_PREFIX + refreshToken);
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("请重新登录");
        }
        TimerUser user = findUserById(userId);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        stringRedisTemplate.opsForValue()
                .set(TOKEN_PREFIX + key, token, Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        String sessionId = createSession(token, refreshToken);
        setSessionCookie(response, sessionId);
        return ResultVO.ok();
    }

    // ==================== 二维码登录 ====================

    @Override
    public ResultVO<QrCodeRes> generateQrCode() {
        String qrCodeId = UUID.randomUUID().toString();
        String statusKey = QR_STATUS_PREFIX + qrCodeId;
        stringRedisTemplate.opsForValue()
                .set(statusKey, "WAIT_SCAN", Duration.ofSeconds(QR_CODE_EXPIRE_SECONDS));
        QrCodeRes qrCodeRes = QrCodeRes.newBuilder()
                .setQrCodeId(qrCodeId)
                .setQrCodeContent(qrCodeId)
                .setExpireTime(QR_CODE_EXPIRE_SECONDS)
                .build();
        log.info("生成二维码: {}", qrCodeId);
        return ResultVO.ok(qrCodeRes);
    }

    @Override
    public ResultVO<Void> scanQrCode(ScanQrCodeReq scanQrCodeReq) {
        String qrCodeId = scanQrCodeReq.getQrCodeId();
        String userId = scanQrCodeReq.getLoginUser().getUserId();
        String statusKey = QR_STATUS_PREFIX + qrCodeId;
        String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!"WAIT_SCAN".equals(currentStatus)) {
            return ResultVO.fail("二维码状态异常");
        }
        TimerUser user = findUserById(userId);
        if (Objects.isNull(user)) {
            return ResultVO.fail(NO_USER);
        }
        Duration ttl = Duration.ofSeconds(QR_CODE_EXPIRE_SECONDS);
        stringRedisTemplate.opsForValue().set(statusKey, SCANNED, ttl);
        stringRedisTemplate.opsForValue()
                .set(QR_PREFIX + qrCodeId + "user", userId, ttl);
        Map<String, String> scanData = Maps.newHashMap();
        scanData.put("userId", userId);
        scanData.put("username", user.getUsername());
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, SCANNED, scanData);
        log.info("二维码已扫描: {}, 用户: {}", qrCodeId, userId);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> confirmQrCode(ConfirmQrCodeReq confirmQrCodeReq) {
        String qrCodeId = confirmQrCodeReq.getQrCodeId();
        String userId = confirmQrCodeReq.getUserId();
        String statusKey = QR_STATUS_PREFIX + qrCodeId;
        String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!SCANNED.equals(currentStatus)) {
            return ResultVO.fail("请先扫描二维码");
        }
        String storedUserId = stringRedisTemplate.opsForValue()
                .get(QR_PREFIX + qrCodeId + "user");
        if (!userId.equals(storedUserId)) {
            return ResultVO.fail("用户信息不匹配");
        }
        TimerUser user = findUserById(userId);
        if (Objects.isNull(user)) {
            return ResultVO.fail(NO_USER);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        String refreshToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue()
                .set(TOKEN_PREFIX + key, token, Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        stringRedisTemplate.opsForValue()
                .set(REFRESH_PREFIX + refreshToken,
                        user.getUserId(), Duration.ofDays(REFRESH_EXPIRE_DAYS));
        String sessionId = createSession(token, refreshToken);
        Map<String, String> confirmData = new HashMap<>();
        confirmData.put("sessionId", sessionId);
        confirmData.put("token", token);
        confirmData.put(REFRESH_TOKEN, refreshToken);
        stringRedisTemplate.opsForValue()
                .set(statusKey, "CONFIRMED", Duration.ofSeconds(60));
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED", confirmData);
        log.info("二维码已确认: {}, 用户: {}, sessionId={}", qrCodeId, userId, sessionId);
        stringRedisTemplate.delete(QR_PREFIX + qrCodeId + "user");
        return ResultVO.ok();
    }

    private ResultVO<Void> doLogin(String authHeader, ServerHttpResponse response) {
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
            return ResultVO.fail(401, "请先登录");
        }
        String[] credentials = parseBasicAuth(authHeader);
        String username = credentials[0];
        String password = credentials[1];
        TimerUser user = findUserById(username);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        if (!user.getPassword().equals(password)) {
            return handleFailCount(username);
        }
        stringRedisTemplate.delete(FAIL_PREFIX + username);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        String refreshToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue()
                .set(TOKEN_PREFIX + key, token, Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        stringRedisTemplate.opsForValue()
                .set(REFRESH_PREFIX + refreshToken,
                        user.getUserId(), Duration.ofDays(REFRESH_EXPIRE_DAYS));
        String sessionId = createSession(token, refreshToken);
        setSessionCookie(response, sessionId);
        log.info("登录成功，会话已创建: sessionId={}, userId={}", sessionId, user.getUserId());
        return ResultVO.ok();
    }

    private String createSession(String accessToken, String refreshToken) {
        String sessionId = UUID.randomUUID().toString();
        Map<String, String> session = new LinkedHashMap<>();
        session.put(SESSION_KEY_TOKEN, accessToken);
        session.put(REFRESH_TOKEN, refreshToken);
        stringRedisTemplate.opsForValue()
                .set(SESSION_PREFIX + sessionId,
                        JSONUtil.toJsonStr(session),
                        Duration.ofDays(REFRESH_EXPIRE_DAYS));
        return sessionId;
    }

    /**
     * 种 HttpOnly cookie（WebFlux 用 ResponseCookie + ServerHttpResponse）
     */
    private void setSessionCookie(ServerHttpResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_SESSION, sessionId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(REFRESH_EXPIRE_DAYS))
                .sameSite("Strict")
                .build();
        response.addCookie(cookie);
    }

    private String[] parseBasicAuth(String authHeader) {
        String basicToken = CharSequenceUtil.subAfter(authHeader, BASIC_AUTH_PREFIX, false);
        if (StringUtils.isBlank(basicToken)) {
            return new String[0];
        }
        byte[] decoded = Base64.getDecoder().decode(basicToken);
        String credentials = new String(decoded, StandardCharsets.UTF_8);
        String[] parts = credentials.split(":", 2);
        if (parts.length < 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
            return new String[0];
        }
        return parts;
    }

    private TimerUser findUserById(String userId) {
        LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(TimerUser::getUserId, userId);
        return timerUserMapper.selectOne(wrapper);
    }

    private ResultVO<Void> handleFailCount(String username) {
        String failKey = FAIL_PREFIX + username;
        Long fails = stringRedisTemplate.opsForValue().increment(failKey);
        stringRedisTemplate.expire(failKey,Duration.ofHours(FAIL_WINDOW_HOURS));
        if (fails != null && fails > FAIL_LIMIT) {
            return ResultVO.fail("请求过于频繁");
        }
        return ResultVO.fail("登录失败");
    }
}