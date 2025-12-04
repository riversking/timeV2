package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.LoginReq;
import com.rivers.proto.LoginRes;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.service.ILoginService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LoginServiceImpl implements ILoginService {

    private final TimerUserMapper timerUserMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public LoginServiceImpl(TimerUserMapper timerUserMapper, StringRedisTemplate stringRedisTemplate) {
        this.timerUserMapper = timerUserMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public ResultVO<LoginRes> login(LoginReq loginReq) {
        String username = loginReq.getUsername();
        String password = loginReq.getPassword();
        // 根据用户名和密码查询用户信息
        if (StringUtils.isBlank(username)) {
            return ResultVO.fail("登录失败");
        }
        if (StringUtils.isBlank(password)) {
            return ResultVO.fail("登录失败");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, username);
        TimerUser user = timerUserMapper.selectOne(userWrapper);
        if (Objects.isNull(user)) {
            return ResultVO.fail("用户不存在");
        }
        String pwd = user.getPassword();
        if (!pwd.equals(password)) {
            return ResultVO.fail("登录失败");
        }
        String userId = user.getUserId();
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(user.getUsername());
        String key = UUID.randomUUID().toString();
        String token = JwtUtil.createJwt(loginUser, key);
        stringRedisTemplate.opsForValue().set("token:" + key, token);
        stringRedisTemplate.expire("token:" + key, 30, TimeUnit.MINUTES);
        return ResultVO.ok(LoginRes.newBuilder().setToken(token).build());
    }
}
