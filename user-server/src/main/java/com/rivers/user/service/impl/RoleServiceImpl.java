package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.activerecord.AbstractModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.protobuf.ProtocolStringList;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerRole;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.entity.TimerUserRole;
import com.rivers.user.mapper.TimerRoleMapper;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.mapper.TimerUserRoleMapper;
import com.rivers.user.service.IRoleService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RoleServiceImpl implements IRoleService {

    private final TimerRoleMapper timerRoleMapper;

    private final TimerUserMapper timerUserMapper;

    private final TimerUserRoleMapper timerUserRoleMapper;

    public RoleServiceImpl(TimerRoleMapper timerRoleMapper, TimerUserMapper timerUserMapper,
                           TimerUserRoleMapper timerUserRoleMapper) {
        this.timerRoleMapper = timerRoleMapper;
        this.timerUserMapper = timerUserMapper;
        this.timerUserRoleMapper = timerUserRoleMapper;
    }

    @Override
    public ResultVO<Void> saveRole(SaveRoleReq saveRoleReq) {
        String roleCode = saveRoleReq.getRoleCode();
        String roleName = saveRoleReq.getRoleName();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        if (StringUtils.isBlank(roleName)) {
            return ResultVO.fail("角色名称不能为空");
        }
        LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
        roleWrapper.eq(TimerRole::getRoleCode, roleCode);
        Long count = timerRoleMapper.selectCount(roleWrapper);
        if (count > 0) {
            return ResultVO.fail("角色编码已存在");
        }
        LoginUser loginUser = saveRoleReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerRole timerRole = new TimerRole();
        timerRole.setRoleCode(roleCode);
        timerRole.setRoleName(roleName);
        timerRole.setCreateUser(userId);
        timerRole.setUpdateUser(userId);
        timerRole.insert();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> updateRole(UpdateRoleReq updateRoleReq) {
        String roleCode = updateRoleReq.getRoleCode();
        String roleName = updateRoleReq.getRoleName();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        if (StringUtils.isBlank(roleName)) {
            return ResultVO.fail("角色名称不能为空");
        }
        TimerRole timerRole = getTimerRole(roleCode);
        long id = updateRoleReq.getId();
        if (Objects.nonNull(timerRole) && timerRole.getId() != id) {
            return ResultVO.fail("角色编码已存在");
        }
        LoginUser loginUser = updateRoleReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerRole upTimerRole = new TimerRole();
        upTimerRole.setId(id);
        upTimerRole.setUpdateUser(userId);
        upTimerRole.setRoleName(roleName);
        upTimerRole.setRoleCode(roleCode);
        upTimerRole.updateById();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<RolePageRes> getRolePage(RolePageReq rolePageReq) {
        int currentPage = rolePageReq.getCurrentPage();
        int pageSize = rolePageReq.getPageSize();
        String roleCode = rolePageReq.getRoleCode();
        String roleName = rolePageReq.getRoleName();
        LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
        roleWrapper.like(StringUtils.isNotBlank(roleCode), TimerRole::getRoleCode, roleCode)
                .like(StringUtils.isNotBlank(roleName), TimerRole::getRoleName, roleName);
        Page<TimerRole> page = new Page<>(currentPage, pageSize);
        Page<TimerRole> timerRolePage = timerRoleMapper.selectPage(page, roleWrapper);
        long total = timerRolePage.getTotal();
        if (total == 0) {
            return ResultVO.ok(RolePageRes.newBuilder().build());
        }
        List<TimerRole> records = timerRolePage.getRecords();
        List<Role> list = records.stream()
                .map(i -> {
                    LocalDateTime createTime = i.getCreateTime();
                    LocalDateTime updateTime = i.getUpdateTime();
                    return Role.newBuilder().setId(i.getId())
                            .setRoleCode(i.getRoleCode())
                            .setRoleName(i.getRoleName())
                            .setCreateTime(Objects.requireNonNull(Optional.ofNullable(createTime)
                                    .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .format(c))
                                    .orElse(null)))
                            .setUpdateTime(Objects.requireNonNull(Optional.ofNullable(updateTime)
                                    .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .format(c))
                                    .orElse(null)))
                            .build();
                })
                .toList();
        return ResultVO.ok(RolePageRes.newBuilder().setTotal(total).addAllRoles(list).build());
    }

    @Override
    public ResultVO<Void> deleteRole(DeleteRoleReq deleteRoleReq) {
        String roleCode = deleteRoleReq.getRoleCode();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        TimerRole timerRole = getTimerRole(roleCode);
        Optional.ofNullable(timerRole).ifPresent(AbstractModel::deleteById);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<RoleDetailRes> getRoleDetail(RoleDetailReq roleDetailReq) {
        String roleCode = roleDetailReq.getRoleCode();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        TimerRole timerRole = getTimerRole(roleCode);
        RoleDetailRes roleDetailRes = Optional.ofNullable(timerRole)
                .map(i ->
                        RoleDetailRes.newBuilder()
                                .setId(i.getId())
                                .setRoleCode(i.getRoleCode())
                                .setRoleName(i.getRoleName())
                                .build())
                .orElse(RoleDetailRes.newBuilder().build());
        return ResultVO.ok(roleDetailRes);
    }

    @Override
    public ResultVO<Void> saveUserRole(SaveUserRoleReq saveUserRoleReq) {
        String roleCode = saveUserRoleReq.getRoleCode();
        ProtocolStringList userIdsList = saveUserRoleReq.getUserIdsList();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        if (CollectionUtils.isEmpty(userIdsList)) {
            return ResultVO.fail("用户ID不能为空");
        }
        TimerRole timerRole = getTimerRole(roleCode);
        if (Objects.isNull(timerRole)) {
            return ResultVO.fail("角色不存在");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.in(TimerUser::getUserId, userIdsList);
        List<TimerUser> timerUsers = timerUserMapper.selectList(userWrapper);
        List<String> userIds = timerUsers.stream()
                .map(TimerUser::getUserId)
                .toList();
        List<String> hasUserIds = userIdsList.stream()
                .filter(userIds::contains)
                .toList();
        if (CollectionUtils.isEmpty(hasUserIds)) {
            return ResultVO.fail("用户不存在");
        }
        LambdaQueryWrapper<TimerUserRole> userRoleWrapper = Wrappers.lambdaQuery();
        userRoleWrapper.eq(TimerUserRole::getRoleCode, roleCode)
                .in(TimerUserRole::getUserId, hasUserIds);
        List<TimerUserRole> timerUserRoles = timerUserRoleMapper.selectList(userRoleWrapper);
        List<String> hasRoleUsers = timerUserRoles.stream()
                .map(TimerUserRole::getUserId)
                .toList();
        List<String> noRoleUsers = hasUserIds.stream()
                .filter(i -> !hasRoleUsers.contains(i))
                .toList();
        if (CollectionUtils.isEmpty(noRoleUsers)) {
            return ResultVO.ok();
        }
        LoginUser loginUser = saveUserRoleReq.getLoginUser();
        String userId = loginUser.getUserId();
        List<TimerUserRole> list = noRoleUsers.stream()
                .map(i -> {
                    TimerUserRole timerUserRole = new TimerUserRole();
                    timerUserRole.setRoleCode(roleCode);
                    timerUserRole.setUserId(i);
                    timerUserRole.setCreateUser(userId);
                    timerUserRole.setUpdateUser(userId);
                    return timerUserRole;
                })
                .toList();
        timerUserRoleMapper.insert(list);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> removeUserRole(RemoveUserRoleReq removeUserRoleReq) {
        String roleCode = removeUserRoleReq.getRoleCode();
        ProtocolStringList userIdsList = removeUserRoleReq.getUserIdsList();
        if (StringUtils.isBlank(roleCode)) {
            return ResultVO.fail("角色编码不能为空");
        }
        if (CollectionUtils.isEmpty(userIdsList)) {
            return ResultVO.fail("用户ID不能为空");
        }
        TimerRole timerRole = getTimerRole(roleCode);
        if (Objects.isNull(timerRole)) {
            return ResultVO.fail("角色不存在");
        }
        LambdaQueryWrapper<TimerUserRole> userRoleWrapper = Wrappers.lambdaQuery();
        userRoleWrapper.eq(TimerUserRole::getRoleCode, roleCode)
                .in(TimerUserRole::getUserId, userIdsList);
        timerUserRoleMapper.delete(userRoleWrapper);
        return ResultVO.ok();
    }

    private TimerRole getTimerRole(String roleCode) {
        LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
        roleWrapper.eq(TimerRole::getRoleCode, roleCode);
        return timerRoleMapper.selectOne(roleWrapper);
    }
}
