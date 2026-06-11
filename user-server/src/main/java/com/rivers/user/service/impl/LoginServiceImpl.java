package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author xx
 */
@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    private static final String REDIS_TOKEN_PREFIX = "token:";
    private static final String REDIS_REFRESH_PREFIX = "refresh:token:";
    private static final String REDIS_FAIL_PREFIX = "login:fail:";
    private static final String REDIS_QR_PREFIX = "qr:code:";
    private static final String REDIS_QR_STATUS_PREFIX = "qr:status:";
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    private static final long QR_CODE_EXPIRE_SECONDS = 300L;
    private static final long JWT_EXPIRE_MINUTES = 30L;
    private static final long REFRESH_EXPIRE_DAYS = 30L;
    private static final long FAIL_LIMIT = 5L;
    private static final long FAIL_WINDOW_HOURS = 1L;
    public static final String NO_USER = "用户不存在";
    public static final String SCANNED = "SCANNED";

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

    // ==================== 登录 / 自动登录 / 刷新 ====================

    @Override
    public ResultVO<AutoLoginRes> login(LoginReq loginReq) {
        String username = loginReq.getUsername();
        String password = loginReq.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultVO.fail("登录失败");
        }
        String basicToken = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return doLogin(BASIC_AUTH_PREFIX + basicToken);
    }

    @Override
    public ResultVO<AutoLoginRes> autoLogin(String authHeader) {
        return doLogin(authHeader);
    }

    @Override
    public ResultVO<AutoLoginRes> refresh(AutoLoginReq autoLoginReq) {
        String refreshToken = autoLoginReq.getRefreshToken();
        if (StringUtils.isBlank(refreshToken)) {
            return ResultVO.fail(401, "请先登录");
        }
        String userId = stringRedisTemplate.opsForValue()
                .get(REDIS_REFRESH_PREFIX + refreshToken);
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("请重新登录");
        }
        TimerUser user = findUserById(userId);
        if (user == null) {
            return ResultVO.fail(NO_USER);
        }
        String token = generateAndStoreJwt(user);
        AutoLoginRes autoLoginRes = AutoLoginRes.newBuilder()
                .setToken(token)
                .setRefreshToken(refreshToken)
                .build();
        return ResultVO.ok(autoLoginRes);
    }

    // ==================== 二维码登录 ====================
    @Override
    public ResultVO<QrCodeRes> generateQrCode() {
        String qrCodeId = UUID.randomUUID().toString();
        String statusKey = REDIS_QR_STATUS_PREFIX + qrCodeId;
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
        String statusKey = REDIS_QR_STATUS_PREFIX + qrCodeId;
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
                .set(REDIS_QR_PREFIX + qrCodeId + "user", userId, ttl);
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
        String statusKey = REDIS_QR_STATUS_PREFIX + qrCodeId;

        String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!SCANNED.equals(currentStatus)) {
            return ResultVO.fail("请先扫描二维码");
        }
        String storedUserId = stringRedisTemplate.opsForValue()
                .get(REDIS_QR_PREFIX + qrCodeId + "user");
        if (!userId.equals(storedUserId)) {
            return ResultVO.fail("用户信息不匹配");
        }
        TimerUser user = findUserById(userId);
        if (Objects.isNull(user)) {
            return ResultVO.fail(NO_USER);
        }
        AutoLoginRes loginRes = buildLoginResponse(user);
        Map<String, String> confirmData = new HashMap<>();
        confirmData.put("token", loginRes.getToken());
        confirmData.put("refreshToken", loginRes.getRefreshToken());
        stringRedisTemplate.opsForValue()
                .set(statusKey, "CONFIRMED", Duration.ofSeconds(60));
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED", confirmData);
        log.info("二维码已确认: {}, 用户: {}", qrCodeId, userId);
        stringRedisTemplate.delete(REDIS_QR_PREFIX + qrCodeId + "user");
        return ResultVO.ok();
    }

    // ==================== 私有方法 ====================

    /**
     * 完整登录流程：解析凭证 → 查库验密 → 生成完整 token 对
     */
    private ResultVO<AutoLoginRes> doLogin(String authHeader) {
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
        stringRedisTemplate.delete(REDIS_FAIL_PREFIX + username);
        return ResultVO.ok(buildLoginResponse(user));
    }

    /**
     * 从 Basic Auth 请求头解析 [username, password]，解析失败返回 null
     */
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

    /**
     * 根据 userId 查询用户
     */
    private TimerUser findUserById(String userId) {
        LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(TimerUser::getUserId, userId);
        return timerUserMapper.selectOne(wrapper);
    }

    /**
     * 登录失败计数 + 限流拦截
     */
    private ResultVO<AutoLoginRes> handleFailCount(String username) {
        String failKey = REDIS_FAIL_PREFIX + username;
        Long fails = stringRedisTemplate.opsForValue().increment(failKey);
        stringRedisTemplate.expire(failKey, FAIL_WINDOW_HOURS, TimeUnit.HOURS);
        if (fails != null && fails > FAIL_LIMIT) {
            return ResultVO.fail("请求过于频繁");
        }
        return ResultVO.fail("登录失败");
    }

    /**
     * 首次登录 / 二维码确认登录：生成 JWT + 新 refreshToken
     */
    private AutoLoginRes buildLoginResponse(TimerUser user) {
        String refreshToken = saveRefreshToken(user);
        String token = generateAndStoreJwt(user);
        return AutoLoginRes.newBuilder()
                .setToken(token)
                .setRefreshToken(refreshToken)
                .build();
    }

    /**
     * 生成 JWT 并写入 Redis（30 分钟过期）
     */
    private String generateAndStoreJwt(TimerUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        stringRedisTemplate.opsForValue()
                .set(REDIS_TOKEN_PREFIX + key, token, Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        return token;
    }

    /**
     * 生成 refreshToken 存入 Redis（只存 userId，30 天过期）
     */
    private String saveRefreshToken(TimerUser user) {
        String refreshToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue()
                .set(REDIS_REFRESH_PREFIX + refreshToken,
                        user.getUserId(), REFRESH_EXPIRE_DAYS, TimeUnit.DAYS);
        return refreshToken;
    }
}