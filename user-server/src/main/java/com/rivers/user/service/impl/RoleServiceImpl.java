package com.rivers.user.service.impl;

import com.google.protobuf.ProtocolStringList;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerRole;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.entity.TimerUserRole;
import com.rivers.user.repository.TimerRoleRepository;
import com.rivers.user.repository.TimerUserRepository;
import com.rivers.user.repository.TimerUserRoleRepository;
import com.rivers.user.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoleServiceImpl implements IRoleService {

    public static final String ROLE_EMPTY = "角色编码不能为空";
    private static final Integer NOT_DELETED = 0;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TimerRoleRepository timerRoleRepository;
    private final TimerUserRepository timerUserRepository;
    private final TimerUserRoleRepository timerUserRoleRepository;

    public RoleServiceImpl(TimerRoleRepository timerRoleRepository,
                           TimerUserRepository timerUserRepository,
                           TimerUserRoleRepository timerUserRoleRepository) {
        this.timerRoleRepository = timerRoleRepository;
        this.timerUserRepository = timerUserRepository;
        this.timerUserRoleRepository = timerUserRoleRepository;
    }

    @Override
    public Mono<ResultVO<Void>> saveRole(SaveRoleReq saveRoleReq) {
        if (StringUtils.isBlank(saveRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (StringUtils.isBlank(saveRoleReq.getRoleName())) {
            return Mono.just(ResultVO.fail("角色名称不能为空"));
        }
        String roleCode = saveRoleReq.getRoleCode();
        String currentUserId = saveRoleReq.getLoginUser().getUserId();
        return timerRoleRepository.existsByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.just(ResultVO.<Void>fail("角色编码已存在"));
                    }
                    TimerRole timerRole = new TimerRole();
                    timerRole.setRoleCode(roleCode);
                    timerRole.setRoleName(saveRoleReq.getRoleName());
                    timerRole.setCreateUser(currentUserId);
                    timerRole.setUpdateUser(currentUserId);
                    timerRole.setCreateTime(LocalDateTime.now());
                    timerRole.setUpdateTime(LocalDateTime.now());
                    return timerRoleRepository.save(timerRole).thenReturn(ResultVO.<Void>ok());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("保存角色失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> updateRole(UpdateRoleReq updateRoleReq) {
        if (StringUtils.isBlank(updateRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (StringUtils.isBlank(updateRoleReq.getRoleName())) {
            return Mono.just(ResultVO.fail("角色名称不能为空"));
        }
        long id = updateRoleReq.getId();
        String roleCode = updateRoleReq.getRoleCode();
        String currentUserId = updateRoleReq.getLoginUser().getUserId();
        return timerRoleRepository.findByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(existing -> {
                    if (!existing.getId().equals(id)) {
                        return Mono.just(ResultVO.<Void>fail("角色编码已存在"));
                    }
                    return doUpdateRole(id, roleCode, updateRoleReq.getRoleName(), currentUserId);
                })
                .switchIfEmpty(Mono.defer(
                        () -> doUpdateRole(id, roleCode, updateRoleReq.getRoleName(), currentUserId)))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新角色失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private Mono<ResultVO<Void>> doUpdateRole(long id, String roleCode, String roleName, String currentUserId) {
        return timerRoleRepository.updateRole(id, roleCode, roleName, currentUserId)
                .map(rows -> ResultVO.<Void>ok());
    }

    @Override
    public Mono<ResultVO<RolePageRes>> getRolePage(RolePageReq rolePageReq) {
        int currentPage = Math.max(1, rolePageReq.getCurrentPage());
        int pageSize = Math.clamp(rolePageReq.getPageSize(), 1, 100);
        String roleCode = StringUtils.defaultString(rolePageReq.getRoleCode());
        String roleName = StringUtils.defaultString(rolePageReq.getRoleName());
        long offset = (long) (currentPage - 1) * pageSize;
        return timerRoleRepository.countByFilter(roleCode, roleName)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(RolePageRes.newBuilder().build()));
                    }
                    return timerRoleRepository.pageByFilter(roleCode, roleName, pageSize, offset)
                            .map(this::toProtoRole)
                            .collectList()
                            .map(roles -> ResultVO.ok(RolePageRes.newBuilder()
                                    .setTotal(total)
                                    .addAllRoles(roles)
                                    .build()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("查询角色分页失败", e);
                    return Mono.just(ResultVO.fail("加载角色分页失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteRole(DeleteRoleReq deleteRoleReq) {
        if (StringUtils.isBlank(deleteRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        return timerRoleRepository.findByRoleCodeAndIsDeleted(deleteRoleReq.getRoleCode(), NOT_DELETED)
                .flatMap(role -> timerRoleRepository.softDeleteById(role.getId()))
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(Exception.class, e -> {
                    log.error("删除角色失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<RoleDetailRes>> getRoleDetail(RoleDetailReq roleDetailReq) {
        if (StringUtils.isBlank(roleDetailReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        return timerRoleRepository.findByRoleCodeAndIsDeleted(roleDetailReq.getRoleCode(), NOT_DELETED)
                .map(role -> ResultVO.ok(RoleDetailRes.newBuilder()
                        .setId(role.getId())
                        .setRoleCode(role.getRoleCode())
                        .setRoleName(role.getRoleName())
                        .build()))
                .defaultIfEmpty(ResultVO.ok(RoleDetailRes.newBuilder().build()))
                .onErrorResume(Exception.class, e -> {
                    log.error("查询角色详情失败", e);
                    return Mono.just(ResultVO.fail("查询角色详情失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> saveUserRole(SaveUserRoleReq saveUserRoleReq) {
        if (StringUtils.isBlank(saveUserRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(saveUserRoleReq.getUserIdsList())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        String roleCode = saveUserRoleReq.getRoleCode();
        ProtocolStringList userIdsList = saveUserRoleReq.getUserIdsList();
        String currentUserId = saveUserRoleReq.getLoginUser().getUserId();
        return timerRoleRepository.existsByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(roleExists -> {
                    if (!Boolean.TRUE.equals(roleExists)) {
                        return Mono.just(ResultVO.<Void>fail("角色不存在"));
                    }
                    return bindUsers(roleCode, userIdsList, currentUserId);
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("保存用户角色失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private Mono<ResultVO<Void>> bindUsers(String roleCode, List<String> userIdsList, String currentUserId) {
        return timerUserRepository.findByUserIdInAndIsDeleted(userIdsList, NOT_DELETED)
                .map(TimerUser::getUserId)
                .collectList()
                .flatMap(validUserIds -> {
                    if (CollectionUtils.isEmpty(validUserIds)) {
                        return Mono.just(ResultVO.<Void>fail("用户不存在"));
                    }
                    return timerUserRoleRepository.findByRoleCodeAndUserIdInAndIsDeleted(
                                    roleCode, validUserIds, NOT_DELETED)
                            .map(TimerUserRole::getUserId)
                            .collectList()
                            .flatMap(boundUserIds -> {
                                List<TimerUserRole> toBind = validUserIds.stream()
                                        .filter(id -> !boundUserIds.contains(id))
                                        .map(id -> buildUserRole(roleCode, id, currentUserId))
                                        .toList();
                                if (toBind.isEmpty()) {
                                    return Mono.just(ResultVO.<Void>ok());
                                }
                                return timerUserRoleRepository.saveAll(toBind)
                                        .then(Mono.just(ResultVO.<Void>ok()));
                            });
                });
    }

    private TimerUserRole buildUserRole(String roleCode, String userId, String currentUserId) {
        TimerUserRole userRole = new TimerUserRole();
        userRole.setRoleCode(roleCode);
        userRole.setUserId(userId);
        userRole.setCreateUser(currentUserId);
        userRole.setUpdateUser(currentUserId);
        userRole.setCreateTime(LocalDateTime.now());
        userRole.setUpdateTime(LocalDateTime.now());
        return userRole;
    }

    @Override
    public Mono<ResultVO<Void>> removeUserRole(RemoveUserRoleReq removeUserRoleReq) {
        if (StringUtils.isBlank(removeUserRoleReq.getRoleCode())) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(removeUserRoleReq.getUserIdsList())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        String roleCode = removeUserRoleReq.getRoleCode();
        ProtocolStringList userIds = removeUserRoleReq.getUserIdsList();
        return timerRoleRepository.existsByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(roleExists -> {
                    if (!Boolean.TRUE.equals(roleExists)) {
                        return Mono.just(ResultVO.<Void>fail("角色不存在"));
                    }
                    return timerUserRoleRepository.softDeleteByRoleCodeAndUserIdIn(roleCode, userIds)
                            .thenReturn(ResultVO.<Void>ok());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("移除用户角色失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<RoleUserPageRes>> getUserRolePage(RoleUserPageReq roleUserPageReq) {
        int currentPage = Math.max(1, roleUserPageReq.getCurrentPage());
        int pageSize = Math.clamp(roleUserPageReq.getPageSize(), 1, 100);
        long offset = (long) (currentPage - 1) * pageSize;
        String roleCode = roleUserPageReq.getRoleCode();
        String username = roleUserPageReq.getUsername();
        String userIdKeyword = StringUtils.defaultString(roleUserPageReq.getUserId());
        boolean filterByUsername = StringUtils.isNotBlank(username);
        Mono<List<String>> userIdsMono = filterByUsername
                ? timerUserRepository.findByUsernameContainingAndIsDeleted(username, NOT_DELETED)
                        .map(TimerUser::getUserId)
                        .collectList()
                : Mono.just(List.of());
        return userIdsMono.flatMap(userIds -> {
            if (filterByUsername && userIds.isEmpty()) {
                return Mono.just(ResultVO.ok(RoleUserPageRes.newBuilder().build()));
            }
            Mono<Long> countMono = filterByUsername
                    ? timerUserRoleRepository.countByRoleCodeAndUserIdIn(roleCode, userIds, userIdKeyword)
                    : timerUserRoleRepository.countByRoleCodeAndUserKeyword(roleCode, userIdKeyword);
            return countMono.flatMap(total -> {
                if (total == 0) {
                    return Mono.just(ResultVO.ok(RoleUserPageRes.newBuilder().build()));
                }
                var pageMono = filterByUsername
                        ? timerUserRoleRepository.pageByRoleCodeAndUserIdIn(
                                roleCode, userIds, userIdKeyword, pageSize, offset)
                        : timerUserRoleRepository.pageByRoleCodeAndUserKeyword(
                                roleCode, userIdKeyword, pageSize, offset);
                return pageMono.map(TimerUserRole::getUserId)
                        .collectList()
                        .flatMap(recordUserIds -> buildRoleUserPage(total, recordUserIds));
            });
        }).onErrorResume(Exception.class, e -> {
            log.error("查询用户角色分页失败", e);
            return Mono.just(ResultVO.fail("查询用户角色分页失败"));
        });
    }

    private Mono<ResultVO<RoleUserPageRes>> buildRoleUserPage(long total, List<String> userIds) {
        return timerUserRepository.findByUserIdInAndIsDeleted(userIds, NOT_DELETED)
                .collectList()
                .map(users -> {
                    Map<String, String> userMap = users.stream()
                            .collect(Collectors.toMap(TimerUser::getUserId,
                                    TimerUser::getUsername, (a, b) -> a));
                    List<User> protoUsers = userIds.stream()
                            .map(userId -> User.newBuilder()
                                    .setUserId(userId)
                                    .setUsername(userMap.getOrDefault(userId, ""))
                                    .build())
                            .toList();
                    return ResultVO.ok(RoleUserPageRes.newBuilder()
                            .setTotal(total)
                            .addAllUsers(protoUsers)
                            .build());
                });
    }

    private Role toProtoRole(TimerRole role) {
        return Role.newBuilder()
                .setId(role.getId())
                .setRoleCode(role.getRoleCode())
                .setRoleName(role.getRoleName())
                .setCreateTime(formatTime(role.getCreateTime()))
                .setUpdateTime(formatTime(role.getUpdateTime()))
                .build();
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : DATE_TIME_FORMATTER.format(time);
    }
}
