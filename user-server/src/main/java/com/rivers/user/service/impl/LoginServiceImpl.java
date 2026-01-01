package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.exception.BusinessException;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

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
    public Mono<ResultVO<LoginRes>> login(LoginReq loginReq) {
        // 1. 参数校验（非阻塞，可放主线程）
        if (StringUtils.isBlank(loginReq.getUsername()) || StringUtils.isBlank(loginReq.getPassword())) {
            return Mono.just(ResultVO.fail("登录失败"));
        }
        // 2. 将整个阻塞逻辑包装到 boundedElastic 线程
        return Mono.fromCallable(() -> {
                    String username = loginReq.getUsername();
                    String password = loginReq.getPassword();
                    // 查询用户（MyBatis-Plus 阻塞调用）
                    LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
                    userWrapper.eq(TimerUser::getUserId, username);
                    TimerUser user = timerUserMapper.selectOne(userWrapper);
                    if (Objects.isNull(user)) {
                        throw new BusinessException("用户不存在");
                    }
                    if (!user.getPassword().equals(password)) {
                        throw new BusinessException("登录失败");
                    }
                    // 构建登录用户信息
                    LoginUser loginUser = new LoginUser();
                    loginUser.setUserId(user.getUserId());
                    loginUser.setUsername(user.getUsername());
                    // 生成 token
                    String key = UUID.randomUUID().toString();
                    String token = JwtUtil.createJwt(loginUser, key);
                    // 存入 Redis（同步阻塞）
                    stringRedisTemplate.opsForValue().set("token:" + key, token, Duration.ofMinutes(30));
                    // 构建 Protobuf 响应
                    LoginRes loginRes = LoginRes.newBuilder()
                            .setToken(token)
                            .build();
                    return ResultVO.ok(loginRes);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("登录异常", e);
                    return Mono.just(ResultVO.fail("系统异常，请稍后再试"));
                });
    }
}
