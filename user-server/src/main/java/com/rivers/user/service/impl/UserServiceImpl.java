package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.service.IUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements IUserService {

    private final TimerUserMapper timerUserMapper;

    public UserServiceImpl(TimerUserMapper timerUserMapper) {
        this.timerUserMapper = timerUserMapper;
    }

    @Override
    public ResultVO<ResultVO.EmptyType> saveUser(SaveUserReq saveUserReq) {
        String username = saveUserReq.getUsername();
        String password = saveUserReq.getPassword();
        String phone = saveUserReq.getPhone();
        String mail = saveUserReq.getMail();
        String userId = saveUserReq.getUserId();
        if (StringUtils.isBlank(username)) {
            return ResultVO.fail("用户名不能为空");
        }
        if (StringUtils.isBlank(password)) {
            return ResultVO.fail("密码不能为空");
        }
        if (StringUtils.isBlank(phone)) {
            return ResultVO.fail("手机号不能为空");
        }
        if (StringUtils.isBlank(mail)) {
            return ResultVO.fail("邮箱不能为空");
        }
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("用户ID不能为空");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        Long count = timerUserMapper.selectCount(userWrapper);
        if (count > 0) {
            return ResultVO.fail("用户ID已存在");
        }
        LoginUser loginUser = saveUserReq.getLoginUser();
        String currentUserId = loginUser.getUserId();
        TimerUser timerUser = new TimerUser();
        timerUser.setUsername(username);
        timerUser.setPassword(password);
        timerUser.setUserId(userId);
        timerUser.setPhone(phone);
        timerUser.setMail(mail);
        timerUser.setNickname(saveUserReq.getNickname());
        timerUser.setCreateUser(currentUserId);
        timerUser.setUpdateUser(currentUserId);
        timerUser.insert();
        return ResultVO.ok();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public ResultVO<ResultVO.EmptyType> updateUser(UpdateUserReq updateUserReq) {
        long id = updateUserReq.getId();
        String username = updateUserReq.getUsername();
        String userId = updateUserReq.getUserId();
        String password = updateUserReq.getPassword();
        String phone = updateUserReq.getPhone();
        String mail = updateUserReq.getMail();
        if (StringUtils.isBlank(username)) {
            return ResultVO.fail("用户名不能为空");
        }
        if (StringUtils.isBlank(password)) {
            return ResultVO.fail("密码不能为空");
        }
        if (StringUtils.isBlank(phone)) {
            return ResultVO.fail("手机号不能为空");
        }
        if (StringUtils.isBlank(mail)) {
            return ResultVO.fail("邮箱不能为空");
        }
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("用户ID不能为空");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        TimerUser timerUser = timerUserMapper.selectOne(userWrapper);
        if (timerUser == null) {
            return ResultVO.fail("用户不存在");
        }
        if (timerUser.getId() != id) {
            return ResultVO.fail("用户ID已存在");
        }
        timerUser.setUsername(username);
        timerUser.setPassword(password);
        timerUser.setPhone(phone);
        timerUser.setMail(mail);
        timerUser.setNickname(updateUserReq.getNickname());
        timerUser.setUserId(userId);
        timerUser.updateById();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<UserPageRes> getUserPage(UserPageReq userPageReq) {
        int currentPage = userPageReq.getCurrentPage();
        int pageSize = userPageReq.getPageSize();
        String userId = userPageReq.getUserId();
        String username = userPageReq.getUsername();
        String nickname = userPageReq.getNickname();
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.like(StringUtils.isNotBlank(userId), TimerUser::getUserId, userId)
                .like(StringUtils.isNotBlank(username), TimerUser::getUsername, username)
                .like(StringUtils.isNotBlank(nickname), TimerUser::getNickname, nickname);
        Page<TimerUser> page = Page.of(currentPage, pageSize);
        IPage<TimerUser> timerUserPage = timerUserMapper.selectPage(page, userWrapper);
        long total = timerUserPage.getTotal();
        if (total == 0) {
            return ResultVO.ok(UserPageRes.newBuilder().build());
        }
        List<TimerUser> records = timerUserPage.getRecords();
        List<User> users = records.stream()
                .map(i ->
                        User.newBuilder()
                                .setUserId(i.getUserId())
                                .setUsername(i.getUsername())
                                .setMail(i.getMail())
                                .setNickname(i.getNickname())
                                .setPhone(i.getPhone())
                                .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                        .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                .format(c))
                                        .orElse(""))
                                .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                        .map(u -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                .format(u)).orElse(""))
                                .build()
                )
                .toList();
        return ResultVO.ok(UserPageRes.newBuilder().setTotal(total).addAllUsers(users).build());
    }
}
