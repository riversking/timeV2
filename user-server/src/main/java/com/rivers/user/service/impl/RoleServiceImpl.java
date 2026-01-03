package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.protobuf.ProtocolStringList;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerRole;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.entity.TimerUserRole;
import com.rivers.user.mapper.TimerRoleMapper;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.mapper.TimerUserRoleMapper;
import com.rivers.user.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoleServiceImpl implements IRoleService {

    public static final String ROLE_EMPTY = "角色编码不能为空";
    private final TimerRoleMapper timerRoleMapper;
    private final TimerUserMapper timerUserMapper;
    private final TimerUserRoleMapper timerUserRoleMapper;

    public RoleServiceImpl(TimerRoleMapper timerRoleMapper,
                           TimerUserMapper timerUserMapper,
                           TimerUserRoleMapper timerUserRoleMapper) {
        this.timerRoleMapper = timerRoleMapper;
        this.timerUserMapper = timerUserMapper;
        this.timerUserRoleMapper = timerUserRoleMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveRole(SaveRoleReq saveRoleReq) {
        // 参数校验提前
        if (StringUtils.isBlank(saveRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (StringUtils.isBlank(saveRoleReq.getRoleName())) {
            return Mono.just(ResultVO.fail("角色名称不能为空"));
        }
        return Mono.fromCallable(() -> {
                    String roleCode = saveRoleReq.getRoleCode();
                    LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
                    roleWrapper.eq(TimerRole::getRoleCode, roleCode);
                    Long count = timerRoleMapper.selectCount(roleWrapper);
                    if (count > 0) {
                        throw new BusinessException("角色编码已存在");
                    }
                    LoginUser loginUser = saveRoleReq.getLoginUser();
                    String userId = loginUser.getUserId();
                    TimerRole timerRole = new TimerRole();
                    timerRole.setRoleCode(roleCode);
                    timerRole.setRoleName(saveRoleReq.getRoleName());
                    timerRole.setCreateUser(userId);
                    timerRole.setUpdateUser(userId);
                    timerRole.setCreateTime(LocalDateTime.now());
                    timerRole.setUpdateTime(LocalDateTime.now());
                    timerRole.insert();
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorReturn(ResultVO.fail("系统异常"));
    }

    @Override
    public Mono<ResultVO<Void>> updateRole(UpdateRoleReq updateRoleReq) {
        if (StringUtils.isBlank(updateRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (StringUtils.isBlank(updateRoleReq.getRoleName())) {
            return Mono.just(ResultVO.fail("角色名称不能为空"));
        }
        return Mono.fromCallable(() -> {
                    String roleCode = updateRoleReq.getRoleCode();
                    long id = updateRoleReq.getId();
                    TimerRole existing = getTimerRole(roleCode);
                    if (existing != null && !Objects.equals(existing.getId(), id)) {
                        throw new BusinessException("角色编码已存在");
                    }
                    LoginUser loginUser = updateRoleReq.getLoginUser();
                    String userId = loginUser.getUserId();
                    TimerRole upTimerRole = new TimerRole();
                    upTimerRole.setId(id);
                    upTimerRole.setRoleName(updateRoleReq.getRoleName());
                    upTimerRole.setRoleCode(roleCode);
                    upTimerRole.setUpdateUser(userId);
                    upTimerRole.setUpdateTime(LocalDateTime.now());
                    upTimerRole.updateById();
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        e -> Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorReturn(ResultVO.fail("系统异常"));
    }

    @Override
    public Mono<ResultVO<RolePageRes>> getRolePage(RolePageReq rolePageReq) {
        // 分页参数通常由前端控制，但可加基本保护
        int currentPage = Math.max(1, rolePageReq.getCurrentPage());
        int pageSize = Math.clamp(rolePageReq.getPageSize(), 1, 100);
        var roleCode = rolePageReq.getRoleCode();
        String roleName = rolePageReq.getRoleName();
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerRole> wrapper = Wrappers.lambdaQuery();
                    wrapper.like(StringUtils.isNotBlank(roleCode), TimerRole::getRoleCode, roleCode);
                    wrapper.like(StringUtils.isNotBlank(roleName), TimerRole::getRoleName, roleName);
                    Page<TimerRole> page = new Page<>(currentPage, pageSize);
                    IPage<TimerRole> resultPage = timerRoleMapper.selectPage(page, wrapper);
                    long total = resultPage.getTotal();
                    if (total == 0) {
                        return ResultVO.ok(RolePageRes.newBuilder().build());
                    }
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    List<Role> roles = resultPage.getRecords().stream()
                            .map(r -> Role.newBuilder()
                                    .setId(r.getId())
                                    .setRoleCode(r.getRoleCode())
                                    .setRoleName(r.getRoleName())
                                    .setCreateTime(Optional.ofNullable(r.getCreateTime())
                                            .map(fmt::format).orElse(""))
                                    .setUpdateTime(Optional.ofNullable(r.getUpdateTime())
                                            .map(fmt::format).orElse(""))
                                    .build())
                            .toList();
                    return ResultVO.ok(RolePageRes.newBuilder()
                            .setTotal(total)
                            .addAllRoles(roles)
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(ResultVO.fail("加载角色分页失败"));
    }

    @Override
    public Mono<ResultVO<Void>> deleteRole(DeleteRoleReq deleteRoleReq) {
        if (StringUtils.isBlank(deleteRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        return Mono.fromCallable(() -> {
                    TimerRole role = getTimerRole(deleteRoleReq.getRoleCode());
                    if (role != null) {
                        role.deleteById();
                    }
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(ResultVO.fail("系统异常"));
    }

    @Override
    public Mono<ResultVO<RoleDetailRes>> getRoleDetail(RoleDetailReq roleDetailReq) {
        if (StringUtils.isBlank(roleDetailReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        return Mono.fromCallable(() -> {
                    TimerRole role = getTimerRole(roleDetailReq.getRoleCode());
                    if (role == null) {
                        return ResultVO.ok(RoleDetailRes.newBuilder().build());
                    }
                    return ResultVO.ok(RoleDetailRes.newBuilder()
                            .setId(role.getId())
                            .setRoleCode(role.getRoleCode())
                            .setRoleName(role.getRoleName())
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(ResultVO.fail("查询角色详情失败"));
    }

    @Override
    public Mono<ResultVO<Void>> saveUserRole(SaveUserRoleReq saveUserRoleReq) {
        if (StringUtils.isBlank(saveUserRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(saveUserRoleReq.getUserIdsList())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        return Mono.fromCallable(() -> {
                    String roleCode = saveUserRoleReq.getRoleCode();
                    ProtocolStringList userIdsList = saveUserRoleReq.getUserIdsList();
                    TimerRole role = getTimerRole(roleCode);
                    if (role == null) {
                        throw new BusinessException("角色不存在");
                    }
                    // 查询存在的用户
                    LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
                    userWrapper.in(TimerUser::getUserId, userIdsList);
                    List<TimerUser> validUsers = timerUserMapper.selectList(userWrapper);
                    List<String> validUserIds = validUsers.stream()
                            .map(TimerUser::getUserId)
                            .toList();
                    if (validUserIds.isEmpty()) {
                        throw new BusinessException("用户不存在");
                    }
                    // 查询已绑定该角色的用户
                    LambdaQueryWrapper<TimerUserRole> boundWrapper = Wrappers.lambdaQuery();
                    boundWrapper.eq(TimerUserRole::getRoleCode, roleCode)
                            .in(TimerUserRole::getUserId, validUserIds);
                    List<TimerUserRole> boundRoles = timerUserRoleMapper.selectList(boundWrapper);
                    List<String> boundUserIds = boundRoles.stream()
                            .map(TimerUserRole::getUserId)
                            .toList();
                    // 找出未绑定的用户
                    List<String> toBind = validUserIds.stream()
                            .filter(id -> !boundUserIds.contains(id))
                            .toList();
                    if (toBind.isEmpty()) {
                        return ResultVO.<Void>ok();
                    }
                    String currentUserId = saveUserRoleReq.getLoginUser().getUserId();
                    LocalDateTime now = LocalDateTime.now();
                    List<TimerUserRole> newBindings = toBind.stream()
                            .map(userId -> {
                                TimerUserRole r = new TimerUserRole();
                                r.setRoleCode(roleCode);
                                r.setUserId(userId);
                                r.setCreateUser(currentUserId);
                                r.setUpdateUser(currentUserId);
                                r.setCreateTime(now);
                                r.setUpdateTime(now);
                                return r;
                            })
                            .toList();

                    timerUserRoleMapper.insert(newBindings);
                    return ResultVO.<Void>ok(null);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        e -> Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorReturn(ResultVO.fail("系统异常"));
    }

    @Override
    public Mono<ResultVO<Void>> removeUserRole(RemoveUserRoleReq removeUserRoleReq) {
        if (StringUtils.isBlank(removeUserRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(removeUserRoleReq.getUserIdsList())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        return Mono.fromCallable(() -> {
                    String roleCode = removeUserRoleReq.getRoleCode();
                    TimerRole role = getTimerRole(roleCode);
                    if (role == null) {
                        throw new BusinessException("角色不存在");
                    }
                    LambdaQueryWrapper<TimerUserRole> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerUserRole::getRoleCode, roleCode)
                            .in(TimerUserRole::getUserId, removeUserRoleReq.getUserIdsList());
                    timerUserRoleMapper.delete(wrapper);
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> Mono.just(ResultVO.fail("系统异常")))
                .onErrorReturn(ResultVO.fail("系统异常"));
    }

    @Override
    public Mono<ResultVO<UserRolePageRes>> getUserRolePage(UserRolePageReq userRolePageReq) {
        int currentPage = Math.max(1, userRolePageReq.getCurrentPage());
        int pageSize = Math.clamp(userRolePageReq.getPageSize(), 1, 100);
        String username = userRolePageReq.getUsername();
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerUserRole> wrapper = Wrappers.lambdaQuery();
                    // 根据 username 查 userIds
                    List<String> userIdsFromName = Lists.newArrayList();
                    if (StringUtils.isNotBlank(username)) {
                        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
                        userWrapper.like(TimerUser::getUsername, username);
                        List<TimerUser> users = timerUserMapper.selectList(userWrapper);
                        if (users.isEmpty()) {
                            return ResultVO.ok(UserRolePageRes.newBuilder().build());
                        }
                        List<String> userIds = users.stream()
                                .map(TimerUser::getUserId)
                                .toList();
                        userIdsFromName.addAll(userIds);
                    }
                    // 构建查询条件
                    if (CollectionUtils.isNotEmpty(userIdsFromName)) {
                        wrapper.in(TimerUserRole::getUserId, userIdsFromName);
                    }
                    if (StringUtils.isNotBlank(userRolePageReq.getUserId())) {
                        // 注意：如果同时传了 username 和 userId，这里会 AND，可能不符合预期
                        // 实际业务中建议只支持一种查询方式，或改为 OR
                        wrapper.like(TimerUserRole::getUserId, userRolePageReq.getUserId());
                    }
                    Page<TimerUserRole> page = new Page<>(currentPage, pageSize);
                    IPage<TimerUserRole> resultPage = timerUserRoleMapper.selectPage(page, wrapper);
                    long total = resultPage.getTotal();
                    if (total == 0) {
                        return ResultVO.ok(UserRolePageRes.newBuilder().build());
                    }
                    List<TimerUserRole> records = resultPage.getRecords();
                    List<String> userIds = records.stream()
                            .map(TimerUserRole::getUserId)
                            .distinct()
                            .toList();
                    List<TimerUser> timerUsers = timerUserMapper.selectByIds(userIds);
                    Map<String, String> userMap = timerUsers
                            .stream()
                            .collect(Collectors.toMap(TimerUser::getUserId,
                                    TimerUser::getUsername,
                                    (a, b) -> a));
                    List<User> users = records.stream()
                            .map(r -> User.newBuilder()
                                    .setUserId(r.getUserId())
                                    .setUsername(userMap.getOrDefault(r.getUserId(), ""))
                                    .build())
                            .toList();
                    return ResultVO.ok(UserRolePageRes.newBuilder()
                            .setTotal(total)
                            .addAllUsers(users)
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> Mono.just(ResultVO.fail("系统异常")))
                .onErrorReturn(ResultVO.fail("查询用户角色分页失败"));
    }

    private TimerRole getTimerRole(String roleCode) {
        LambdaQueryWrapper<TimerRole> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(TimerRole::getRoleCode, roleCode);
        return timerRoleMapper.selectOne(wrapper);
    }
}