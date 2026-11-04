package com.rivers.user.service.impl;

import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerMenu;
import com.rivers.user.entity.TimerRoleMenu;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.entity.TimerUserRole;
import com.rivers.user.repository.TimerMenuRepository;
import com.rivers.user.repository.TimerRoleMenuRepository;
import com.rivers.user.repository.TimerUserRepository;
import com.rivers.user.repository.TimerUserRoleRepository;
import com.rivers.user.service.IUserService;
import com.rivers.user.util.PasswordGenerator;
import com.rivers.user.vo.MenuTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    public static final String USER_EMPTY = "用户ID不能为空";
    private static final Integer NOT_DELETED = 0;
    private static final String ONLINE_KEY_PREFIX = "user:online:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TimerUserRepository timerUserRepository;
    private final TimerUserRoleRepository timerUserRoleRepository;
    private final TimerRoleMenuRepository timerRoleMenuRepository;
    private final TimerMenuRepository timerMenuRepository;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    public UserServiceImpl(TimerUserRepository timerUserRepository,
                           TimerUserRoleRepository timerUserRoleRepository,
                           TimerRoleMenuRepository timerRoleMenuRepository,
                           TimerMenuRepository timerMenuRepository,
                           ReactiveStringRedisTemplate reactiveRedisTemplate) {
        this.timerUserRepository = timerUserRepository;
        this.timerUserRoleRepository = timerUserRoleRepository;
        this.timerRoleMenuRepository = timerRoleMenuRepository;
        this.timerMenuRepository = timerMenuRepository;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    @Override
    public Mono<ResultVO<Void>> saveUser(SaveUserReq saveUserReq) {
        if (StringUtils.isBlank(saveUserReq.getUsername())) {
            return Mono.just(ResultVO.fail("用户名不能为空"));
        }
        if (StringUtils.isBlank(saveUserReq.getPhone())) {
            return Mono.just(ResultVO.fail("手机号不能为空"));
        }
        if (StringUtils.isBlank(saveUserReq.getMail())) {
            return Mono.just(ResultVO.fail("邮箱不能为空"));
        }
        if (StringUtils.isBlank(saveUserReq.getUserId())) {
            return Mono.just(ResultVO.fail(USER_EMPTY));
        }
        String userId = saveUserReq.getUserId();
        String currentUserId = saveUserReq.getLoginUser().getUserId();
        return timerUserRepository.existsByUserIdAndIsDeleted(userId, NOT_DELETED)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.just(ResultVO.<Void>fail("用户ID已存在"));
                    }
                    TimerUser timerUser = new TimerUser();
                    timerUser.setUsername(saveUserReq.getUsername());
                    timerUser.setUserId(userId);
                    timerUser.setPassword(PasswordGenerator.generateComplexPassword(16));
                    timerUser.setPhone(saveUserReq.getPhone());
                    timerUser.setMail(saveUserReq.getMail());
                    timerUser.setUserCode(UUID.randomUUID().toString());
                    timerUser.setCreateUser(currentUserId);
                    timerUser.setUpdateUser(currentUserId);
                    timerUser.setCreateTime(LocalDateTime.now());
                    timerUser.setUpdateTime(LocalDateTime.now());
                    return timerUserRepository.save(timerUser).thenReturn(ResultVO.<Void>ok());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("保存用户失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> updateUser(UpdateUserReq updateUserReq) {
        if (StringUtils.isBlank(updateUserReq.getUsername())) {
            return Mono.just(ResultVO.fail("用户名不能为空"));
        }
        if (StringUtils.isBlank(updateUserReq.getPhone())) {
            return Mono.just(ResultVO.fail("手机号不能为空"));
        }
        if (StringUtils.isBlank(updateUserReq.getMail())) {
            return Mono.just(ResultVO.fail("邮箱不能为空"));
        }
        if (StringUtils.isBlank(updateUserReq.getUserId())) {
            return Mono.just(ResultVO.fail(USER_EMPTY));
        }
        long id = updateUserReq.getId();
        String userId = updateUserReq.getUserId();
        String currentUserId = updateUserReq.getLoginUser().getUserId();
        return timerUserRepository.findByUserIdAndIsDeleted(userId, NOT_DELETED)
                .flatMap(existing -> {
                    if (!existing.getId().equals(id)) {
                        return Mono.just(ResultVO.<Void>fail("用户ID已存在"));
                    }
                    return updateUserInfo(id, updateUserReq, currentUserId);
                })
                .switchIfEmpty(Mono.defer(() -> updateUserInfo(id, updateUserReq, currentUserId)))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新用户失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private Mono<ResultVO<Void>> updateUserInfo(long id, UpdateUserReq updateUserReq, String currentUserId) {
        return timerUserRepository.updateUserInfo(id, updateUserReq.getUsername(), updateUserReq.getPhone(),
                        updateUserReq.getMail(), updateUserReq.getUserId(), currentUserId)
                .map(rows -> ResultVO.<Void>ok());
    }

    @Override
    public Mono<ResultVO<UserPageRes>> getUserPage(UserPageReq userPageReq) {
        int currentPage = Math.max(1, userPageReq.getCurrentPage());
        int pageSize = Math.clamp(userPageReq.getPageSize(), 1, 100);
        String userId = StringUtils.defaultString(userPageReq.getUserId());
        String username = StringUtils.defaultString(userPageReq.getUsername());
        long offset = (long) (currentPage - 1) * pageSize;
        return timerUserRepository.countByFilter(userId, username)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(UserPageRes.newBuilder().build()));
                    }
                    return timerUserRepository.pageByFilter(userId, username, pageSize, offset)
                            .map(this::toProtoUser)
                            .collectList()
                            .map(users -> ResultVO.ok(UserPageRes.newBuilder()
                                    .setTotal(total)
                                    .addAllUsers(users)
                                    .build()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("查询用户分页失败", e);
                    return Mono.just(ResultVO.fail("加载用户分页失败"));
                });
    }

    @Override
    public Mono<ResultVO<UserDetailRes>> getUserDetail(UserReq userReq) {
        if (StringUtils.isBlank(userReq.getUserId())) {
            return Mono.just(ResultVO.fail(USER_EMPTY));
        }
        return timerUserRepository.findByUserIdAndIsDeleted(userReq.getUserId(), NOT_DELETED)
                .map(user -> ResultVO.ok(UserDetailRes.newBuilder()
                        .setId(user.getId())
                        .setUserId(user.getUserId())
                        .setUsername(user.getUsername())
                        .setMail(user.getMail())
                        .setPhone(user.getPhone())
                        .build()))
                .switchIfEmpty(Mono.just(ResultVO.<UserDetailRes>fail("用户不存在")))
                .onErrorResume(Exception.class, e -> {
                    log.error("查询用户详情失败", e);
                    return Mono.just(ResultVO.fail("查询用户详情失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteUser(UserReq userReq) {
        if (StringUtils.isBlank(userReq.getUserId())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        return timerUserRepository.softDeleteByUserId(userReq.getUserId())
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(Exception.class, e -> {
                    log.error("删除用户失败", e);
                    return Mono.just(ResultVO.fail("删除用户失败"));
                });
    }

    @Override
    public Mono<ResultVO<List<MenuTreeVO>>> ownedMenuTree(OwnedMenuTreeReq ownedMenuTreeReq) {
        LoginUser loginUser = ownedMenuTreeReq.getLoginUser();
        if (StringUtils.isBlank(loginUser.getUserId())) {
            return Mono.just(ResultVO.fail("当前用户未登录"));
        }
        String userId = loginUser.getUserId();
        return timerUserRoleRepository.findByUserIdAndIsDeleted(userId, NOT_DELETED)
                .map(TimerUserRole::getRoleCode)
                .collectList()
                .flatMap(roleCodes -> {
                    if (CollectionUtils.isEmpty(roleCodes)) {
                        return Mono.just(ResultVO.<List<MenuTreeVO>>fail("获取菜单失败"));
                    }
                    return timerRoleMenuRepository.findByRoleCodeInAndIsDeleted(roleCodes, NOT_DELETED)
                            .map(TimerRoleMenu::getMenuCode)
                            .distinct()
                            .collectList()
                            .flatMap(menuCodes -> {
                                if (CollectionUtils.isEmpty(menuCodes)) {
                                    return Mono.just(ResultVO.<List<MenuTreeVO>>fail("获取菜单失败"));
                                }
                                return timerMenuRepository.findByMenuCodeInAndIsDeleted(menuCodes, NOT_DELETED)
                                        .map(this::toMenuTreeVO)
                                        .collectList()
                                        .flatMap(menus -> {
                                            if (CollectionUtils.isEmpty(menus)) {
                                                return Mono.just(ResultVO.<List<MenuTreeVO>>fail("获取菜单失败"));
                                            }
                                            menus.sort(Comparator.comparing(MenuTreeVO::getSortOrder));
                                            TreeFactory<Long, MenuTreeVO> treeFactory = new TreeFactory<>();
                                            return Mono.just(ResultVO.ok(treeFactory.buildTree(menus)));
                                        });
                            });
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("获取用户菜单失败", e);
                    return Mono.just(ResultVO.fail("获取菜单失败"));
                });
    }

    @Override
    public Mono<ResultVO<UserDetailRes>> getCurrentUser(CommonReq commonReq) {
        String userId = commonReq.getLoginUser().getUserId();
        return timerUserRepository.findByUserIdAndIsDeleted(userId, NOT_DELETED)
                .map(user -> ResultVO.ok(UserDetailRes.newBuilder()
                        .setUserId(user.getUserId())
                        .setUsername(user.getUsername())
                        .setMail(user.getMail())
                        .setPhone(user.getPhone())
                        .build()))
                .defaultIfEmpty(ResultVO.ok(UserDetailRes.newBuilder().build()))
                .onErrorResume(Exception.class, e -> {
                    log.error("获取当前用户失败", e);
                    return Mono.just(ResultVO.fail("获取用户信息失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> enableUser(EnableUserReq enableUserReq) {
        return changeUserStatus(enableUserReq.getUserId(), "0", enableUserReq.getLoginUser());
    }

    @Override
    public Mono<ResultVO<Void>> disableUser(DisableUserReq disableUserReq) {
        return changeUserStatus(disableUserReq.getUserId(), "1", disableUserReq.getLoginUser());
    }

    private Mono<ResultVO<Void>> changeUserStatus(String userId, String isDisable, LoginUser loginUser) {
        return timerUserRepository.updateDisable(userId, isDisable, loginUser.getUserId())
                .flatMap(rows -> rows == 0
                        ? Mono.just(ResultVO.<Void>fail("用户不存在"))
                        : Mono.just(ResultVO.<Void>ok()))
                .onErrorResume(Exception.class, e -> {
                    log.error("修改用户状态失败", e);
                    return Mono.just(ResultVO.fail("修改用户状态失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> resetPassword(ResetPasswordReq resetPasswordReq) {
        String newPassword = resetPasswordReq.getNewPassword();
        String oldPassword = resetPasswordReq.getOldPassword();
        String userId = resetPasswordReq.getLoginUser().getUserId();
        if (StringUtils.isEmpty(newPassword)) {
            return Mono.just(ResultVO.fail("新密码不能为空"));
        }
        if (StringUtils.isEmpty(oldPassword)) {
            return Mono.just(ResultVO.fail("旧密码不能为空"));
        }
        return timerUserRepository.findByUserIdAndIsDeleted(userId, NOT_DELETED)
                .flatMap(user -> {
                    if (!user.getPassword().equals(oldPassword)) {
                        return Mono.just(ResultVO.<Void>fail("旧密码错误"));
                    }
                    return timerUserRepository.updatePassword(userId, newPassword)
                            .thenReturn(ResultVO.<Void>ok());
                })
                .switchIfEmpty(Mono.just(ResultVO.<Void>fail("用户不存在")))
                .onErrorResume(Exception.class, e -> {
                    log.error("重置密码失败", e);
                    return Mono.just(ResultVO.fail("重置密码失败"));
                });
    }

    @Override
    public Mono<ResultVO<UserPageRes>> getUserActivePage(UserPageReq userPageReq) {
        int currentPage = Math.max(1, userPageReq.getCurrentPage());
        int pageSize = Math.clamp(userPageReq.getPageSize(), 1, 100);
        String userId = StringUtils.defaultString(userPageReq.getUserId());
        String username = StringUtils.defaultString(userPageReq.getUsername());
        long offset = (long) (currentPage - 1) * pageSize;
        return timerUserRepository.countByFilter(userId, username)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(UserPageRes.newBuilder().build()));
                    }
                    return timerUserRepository.pageByFilter(userId, username, pageSize, offset)
                            .concatMap(this::toProtoUserWithOnline)
                            .collectList()
                            .map(users -> ResultVO.ok(UserPageRes.newBuilder()
                                    .setTotal(total)
                                    .addAllUsers(users)
                                    .build()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("批量查询在线状态失败", e);
                    return Mono.just(ResultVO.fail("批量查询在线状态失败"));
                });
    }

    private Mono<User> toProtoUserWithOnline(TimerUser timerUser) {
        return reactiveRedisTemplate.hasKey(ONLINE_KEY_PREFIX + timerUser.getUserId())
                .defaultIfEmpty(false)
                .map(online -> toProtoUser(timerUser).toBuilder()
                        .setIsActive(Boolean.TRUE.equals(online) ? "1" : "0")
                        .build());
    }

    private User toProtoUser(TimerUser user) {
        return User.newBuilder()
                .setId(user.getId())
                .setUserId(user.getUserId())
                .setUsername(user.getUsername())
                .setMail(user.getMail())
                .setPhone(user.getPhone())
                .setIsDisable(user.getIsDisable())
                .setCreateTime(formatTime(user.getCreateTime()))
                .setUpdateTime(formatTime(user.getUpdateTime()))
                .build();
    }

    private MenuTreeVO toMenuTreeVO(TimerMenu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuCode(menu.getMenuCode());
        vo.setMenuType(menu.getMenuType());
        vo.setRoutePath(menu.getRoutePath());
        vo.setIcon(menu.getIcon());
        vo.setComponent(menu.getComponent());
        vo.setSortOrder(menu.getSortOrder());
        return vo;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : DATE_TIME_FORMATTER.format(time);
    }
}
