package com.rivers.user.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.AutoLoginReq;
import com.rivers.proto.AutoLoginRes;
import com.rivers.proto.LoginReq;
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
    private final TimerUserMapper timerUserMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public LoginServiceImpl(TimerUserMapper timerUserMapper, StringRedisTemplate stringRedisTemplate) {
        this.timerUserMapper = timerUserMapper;
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
}
