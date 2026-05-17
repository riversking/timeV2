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
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author xx
 */
@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    public static final String REFRESH_TOKEN = "refresh:token:";
    public static final String BASIC = "Basic ";
    public static final String QR_CODE_PREFIX = "qr:code:";
    public static final String QR_CODE_STATUS_PREFIX = "qr:status:";
    private static final long QR_CODE_EXPIRE_TIME = 300L;

    private final TimerUserMapper timerUserMapper;

    private final QrCodeWebSocketHandler qrCodeWebSocketHandler;

    private final StringRedisTemplate stringRedisTemplate;

    public LoginServiceImpl(TimerUserMapper timerUserMapper, QrCodeWebSocketHandler qrCodeWebSocketHandler,
                            StringRedisTemplate stringRedisTemplate) {
        this.timerUserMapper = timerUserMapper;
        this.qrCodeWebSocketHandler = qrCodeWebSocketHandler;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public ResultVO<AutoLoginRes> login(LoginReq loginReq) {
        // 1. 参数校验（非阻塞，可放主线程）
        String username = loginReq.getUsername();
        String password = loginReq.getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultVO.fail("登录失败");
        }
        String basicToken = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        // 2. 将整个阻塞逻辑包装到 boundedElastic 线程
        return autoLogin(BASIC + basicToken);
    }

    @Override
    public ResultVO<AutoLoginRes> autoLogin(String authHeader) {
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BASIC)) {
            return ResultVO.fail(401, "请先登录");
        }
        String basicToken = CharSequenceUtil.subAfter(authHeader, BASIC, false);
        if (StringUtils.isBlank(basicToken)) {
            return ResultVO.fail(401, "请先登录");
        }
        byte[] decodeToken = Base64.getDecoder().decode(basicToken);
        String credentials = new String(decodeToken, StandardCharsets.UTF_8);
        String[] parts = credentials.split(":", 2);
        String username = parts[0];
        String password = parts[1];
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, username);
        TimerUser user = timerUserMapper.selectOne(userWrapper);
        if (Objects.isNull(user)) {
            return ResultVO.fail("用户不存在");
        }
        if (!user.getPassword().equals(password)) {
            String failKey = "login:fail:" + username;
            Long fails = stringRedisTemplate.opsForValue().increment(failKey);
            stringRedisTemplate.expire(failKey, 1, TimeUnit.HOURS);
            if (fails != null && fails > 5) {
                return ResultVO.fail("请求过于频繁");
            }
            return ResultVO.fail("登录失败");
        }
        // 清除失败计数
        stringRedisTemplate.delete("login:fail:" + username);
        String refreshToken = UUID.randomUUID().toString();
        String refreshKey = REFRESH_TOKEN + refreshToken;
        stringRedisTemplate.opsForValue().set(refreshKey, authHeader, 30L, TimeUnit.DAYS);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        // 生成 token
        String token = JwtUtil.createJwt(loginUser, key);
        // 存入 Redis（同步阻塞）
        stringRedisTemplate.opsForValue().set("token:" + key, token, Duration.ofMinutes(30));
        AutoLoginRes autoLoginRes = AutoLoginRes.newBuilder()
                .setToken(token)
                .setRefreshToken(refreshToken)
                .build();
        return ResultVO.ok(autoLoginRes);
    }

    @Override
    public ResultVO<AutoLoginRes> refresh(AutoLoginReq autoLoginReq) {
        String refreshToken = autoLoginReq.getRefreshToken();
        if (StringUtils.isBlank(refreshToken)) {
            return ResultVO.fail(401, "请先登录");
        }
        String authHeader = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN + refreshToken);
        if (StringUtils.isBlank(authHeader)) {
            return ResultVO.fail("请重新登录");
        }
        stringRedisTemplate.delete(REFRESH_TOKEN + refreshToken);
        return autoLogin(authHeader);
    }

    @Override
    public ResultVO<QrCodeRes> generateQrCode() {
        String qrCodeId = UUID.randomUUID().toString();
        String statusKey = QR_CODE_STATUS_PREFIX + qrCodeId;
        stringRedisTemplate.opsForValue().set(statusKey, "WAIT_SCAN", Duration.ofSeconds(QR_CODE_EXPIRE_TIME));
        QrCodeRes qrCodeRes = QrCodeRes.newBuilder()
                .setQrCodeId(qrCodeId)
                .setQrCodeContent(qrCodeId)
                .setExpireTime(QR_CODE_EXPIRE_TIME)
                .build();
        log.info("生成二维码: {}", qrCodeId);
        return ResultVO.ok(qrCodeRes);
    }

    @Override
    public ResultVO<Void> scanQrCode(ScanQrCodeReq scanQrCodeReq) {
        String qrCodeId = scanQrCodeReq.getQrCodeId();
        com.rivers.proto.LoginUser loginUser = scanQrCodeReq.getLoginUser();
        String statusKey = QR_CODE_STATUS_PREFIX + qrCodeId;
        String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!"WAIT_SCAN".equals(currentStatus)) {
            return ResultVO.fail("二维码状态异常");
        }
        String userId = loginUser.getUserId();
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        TimerUser user = timerUserMapper.selectOne(userWrapper);
        if (Objects.isNull(user)) {
            return ResultVO.fail("用户不存在");
        }
        stringRedisTemplate.opsForValue().set(statusKey, "SCANNED", Duration.ofSeconds(QR_CODE_EXPIRE_TIME));
        stringRedisTemplate.opsForValue().set(QR_CODE_PREFIX + qrCodeId + "user", userId, Duration.ofSeconds(QR_CODE_EXPIRE_TIME));
        Map<String, String> scanData = Maps.newHashMap();
        scanData.put("userId", userId);
        scanData.put("username", user.getUsername());
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "SCANNED", scanData);
        log.info("二维码已扫描: {}, 用户: {}", qrCodeId, userId);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> confirmQrCode(ConfirmQrCodeReq confirmQrCodeReq) {
        String qrCodeId = confirmQrCodeReq.getQrCodeId();
        String userId = confirmQrCodeReq.getUserId();
        String statusKey = QR_CODE_STATUS_PREFIX + qrCodeId;
        String currentStatus = stringRedisTemplate.opsForValue().get(statusKey);
        if (StringUtils.isBlank(currentStatus)) {
            return ResultVO.fail("二维码已过期");
        }
        if (!"SCANNED".equals(currentStatus)) {
            return ResultVO.fail("请先扫描二维码");
        }
        String storedUserId = stringRedisTemplate.opsForValue().get(QR_CODE_PREFIX + qrCodeId + "user");
        if (!userId.equals(storedUserId)) {
            return ResultVO.fail("用户信息不匹配");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        TimerUser user = timerUserMapper.selectOne(userWrapper);
        if (Objects.isNull(user)) {
            return ResultVO.fail("用户不存在");
        }
        String refreshToken = UUID.randomUUID().toString();
        String refreshKey = REFRESH_TOKEN + refreshToken;
        String credentials = user.getUserId() + ":" + user.getPassword();
        String basicToken = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = BASIC + basicToken;
        stringRedisTemplate.opsForValue().set(refreshKey, authHeader, 30L, TimeUnit.DAYS);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        stringRedisTemplate.opsForValue().set("token:" + key, token, Duration.ofMinutes(30));
        Map<String, String> confirmData = new java.util.HashMap<>();
        confirmData.put("token", token);
        confirmData.put("refreshToken", refreshToken);
        stringRedisTemplate.opsForValue().set(statusKey, "CONFIRMED", Duration.ofSeconds(60));
        qrCodeWebSocketHandler.sendQrCodeStatus(qrCodeId, "CONFIRMED", confirmData);
        log.info("二维码已确认: {}, 用户: {}", qrCodeId, userId);
        stringRedisTemplate.delete(QR_CODE_PREFIX + qrCodeId + "user");
        return ResultVO.ok();
    }
}
