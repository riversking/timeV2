package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerMenu;
import com.rivers.user.entity.TimerRoleMenu;
import com.rivers.user.entity.TimerUser;
import com.rivers.user.entity.TimerUserRole;
import com.rivers.user.mapper.TimerMenuMapper;
import com.rivers.user.mapper.TimerRoleMenuMapper;
import com.rivers.user.mapper.TimerUserMapper;
import com.rivers.user.mapper.TimerUserRoleMapper;
import com.rivers.user.service.IUserService;
import com.rivers.user.vo.MenuTreeVO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements IUserService {

    public static final String USER_EMPTY = "用户ID不能为空";
    private final TimerUserMapper timerUserMapper;
    private final TimerUserRoleMapper timerUserRoleMapper;
    private final TimerRoleMenuMapper timerRoleMenuMapper;
    private final TimerMenuMapper timerMenuMapper;

    public UserServiceImpl(TimerUserMapper timerUserMapper,
                           TimerUserRoleMapper timerUserRoleMapper,
                           TimerRoleMenuMapper timerRoleMenuMapper,
                           TimerMenuMapper timerMenuMapper) {
        this.timerUserMapper = timerUserMapper;
        this.timerUserRoleMapper = timerUserRoleMapper;
        this.timerRoleMenuMapper = timerRoleMenuMapper;
        this.timerMenuMapper = timerMenuMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveUser(SaveUserReq saveUserReq) {
        if (StringUtils.isBlank(saveUserReq.getUsername())) {
            return Mono.just(ResultVO.fail("用户名不能为空"));
        }
        if (StringUtils.isBlank(saveUserReq.getPassword())) {
            return Mono.just(ResultVO.fail("密码不能为空"));
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
        return Mono.fromCallable(() -> {
                    String userId = saveUserReq.getUserId();
                    LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerUser::getUserId, userId);
                    Long count = timerUserMapper.selectCount(wrapper);
                    if (count > 0) {
                        throw new BusinessException("用户ID已存在");
                    }
                    LoginUser loginUser = saveUserReq.getLoginUser();
                    String currentUserId = loginUser.getUserId();
                    TimerUser timerUser = new TimerUser();
                    timerUser.setUsername(saveUserReq.getUsername());
                    timerUser.setPassword(saveUserReq.getPassword());
                    timerUser.setUserId(userId);
                    timerUser.setPhone(saveUserReq.getPhone());
                    timerUser.setMail(saveUserReq.getMail());
                    timerUser.setNickname(saveUserReq.getNickname());
                    timerUser.setCreateUser(currentUserId);
                    timerUser.setUpdateUser(currentUserId);
                    timerUser.insert();

                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        ex -> Mono.just(ResultVO.fail(ex.getMessage())));
    }

    @Override
    public Mono<ResultVO<Void>> updateUser(UpdateUserReq updateUserReq) {
        if (StringUtils.isBlank(updateUserReq.getUsername())) {
            return Mono.just(ResultVO.fail("用户名不能为空"));
        }
        if (StringUtils.isBlank(updateUserReq.getPassword())) {
            return Mono.just(ResultVO.fail("密码不能为空"));
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
        return Mono.fromCallable(() -> {
                    long id = updateUserReq.getId();
                    String userId = updateUserReq.getUserId();
                    LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerUser::getUserId, userId);
                    TimerUser existing = timerUserMapper.selectOne(wrapper);
                    if (existing != null && existing.getId() != id) {
                        throw new BusinessException("用户ID已存在");
                    }
                    LoginUser loginUser = updateUserReq.getLoginUser();
                    String currentUserId = loginUser.getUserId();
                    TimerUser user = new TimerUser();
                    user.setId(id);
                    user.setUsername(updateUserReq.getUsername());
                    user.setPassword(updateUserReq.getPassword());
                    user.setPhone(updateUserReq.getPhone());
                    user.setMail(updateUserReq.getMail());
                    user.setNickname(updateUserReq.getNickname());
                    user.setUserId(userId);
                    user.setUpdateUser(currentUserId);
                    user.updateById();
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        ex -> Mono.just(ResultVO.fail(ex.getMessage())));
    }

    @Override
    public Mono<ResultVO<UserPageRes>> getUserPage(UserPageReq userPageReq) {
        int currentPage = Math.max(1, userPageReq.getCurrentPage());
        int pageSize = Math.clamp(userPageReq.getPageSize(), 1, 100);
        return Mono.fromCallable(() -> {
            String userId = userPageReq.getUserId();
            String username = userPageReq.getUsername();
            String nickname = userPageReq.getNickname();
            LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
            wrapper.like(StringUtils.isNotBlank(userId), TimerUser::getUserId, userId)
                    .like(StringUtils.isNotBlank(username), TimerUser::getUsername, username)
                    .like(StringUtils.isNotBlank(nickname), TimerUser::getNickname, nickname);
            Page<TimerUser> page = Page.of(currentPage, pageSize);
            IPage<TimerUser> result = timerUserMapper.selectPage(page, wrapper);
            long total = result.getTotal();
            if (total == 0) {
                return ResultVO.ok(UserPageRes.newBuilder().build());
            }
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<User> users = result.getRecords().stream()
                    .map(u -> User.newBuilder()
                            .setUserId(u.getUserId())
                            .setUsername(u.getUsername())
                            .setMail(u.getMail())
                            .setNickname(u.getNickname())
                            .setPhone(u.getPhone())
                            .setCreateTime(Optional.ofNullable(u.getCreateTime())
                                    .map(dateTimeFormatter::format)
                                    .orElse(""))
                            .setUpdateTime(Optional.ofNullable(u.getUpdateTime())
                                    .map(dateTimeFormatter::format)
                                    .orElse(""))
                            .build())
                    .toList();

            UserPageRes res = UserPageRes.newBuilder()
                    .setTotal(total)
                    .addAllUsers(users)
                    .build();

            return ResultVO.ok(res);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ResultVO<UserDetailRes>> getUserDetail(UserReq userReq) {
        if (StringUtils.isBlank(userReq.getUserId())) {
            return Mono.just(ResultVO.<UserDetailRes>fail(USER_EMPTY));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerUser::getUserId, userReq.getUserId());
                    TimerUser user = timerUserMapper.selectOne(wrapper);
                    if (user == null) {
                        throw new BusinessException("用户不存在");
                    }
                    UserDetailRes res = UserDetailRes.newBuilder()
                            .setId(user.getId())
                            .setUserId(user.getUserId())
                            .setUsername(user.getUsername())
                            .setMail(user.getMail())
                            .setNickname(user.getNickname())
                            .setPhone(user.getPhone())
                            .build();
                    return ResultVO.ok(res);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        ex -> Mono.just(ResultVO.fail(ex.getMessage())));
    }

    @Override
    public Mono<ResultVO<Void>> deleteUser(UserReq userReq) {
        if (StringUtils.isBlank(userReq.getUserId())) {
            return Mono.just(ResultVO.fail("用户不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerUser> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerUser::getUserId, userReq.getUserId());
                    TimerUser user = timerUserMapper.selectOne(wrapper);
                    if (user != null) {
                        user.deleteById();
                    }
                    // 幂等：用户不存在也视为成功
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic());
        // 无 BusinessException，无需 onErrorResume
    }

    @Override
    public Mono<ResultVO<List<MenuTreeVO>>> ownedMenuTree(OwnedMenuTreeReq ownedMenuTreeReq) {
        LoginUser loginUser = ownedMenuTreeReq.getLoginUser();
        if (StringUtils.isBlank(loginUser.getUserId())) {
            return Mono.just(ResultVO.fail("当前用户未登录"));
        }
        return Mono.fromCallable(() -> {
                    String userId = loginUser.getUserId();
                    LambdaQueryWrapper<TimerUserRole> userRoleWrapper = Wrappers.lambdaQuery();
                    userRoleWrapper.eq(TimerUserRole::getUserId, userId);
                    List<TimerUserRole> userRoles = timerUserRoleMapper.selectList(userRoleWrapper);
                    if (CollectionUtils.isEmpty(userRoles)) {
                        throw new BusinessException("获取菜单失败");
                    }
                    List<String> roleCodes = userRoles.stream()
                            .map(TimerUserRole::getRoleCode)
                            .toList();
                    LambdaQueryWrapper<TimerRoleMenu> roleMenuWrapper = Wrappers.lambdaQuery();
                    roleMenuWrapper.in(TimerRoleMenu::getRoleCode, roleCodes);
                    List<TimerRoleMenu> roleMenus = timerRoleMenuMapper.selectList(roleMenuWrapper);
                    if (CollectionUtils.isEmpty(roleMenus)) {
                        throw new BusinessException("获取菜单失败");
                    }
                    List<String> menuCodes = roleMenus.stream()
                            .map(TimerRoleMenu::getMenuCode)
                            .distinct()
                            .toList();
                    LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
                    menuWrapper.in(TimerMenu::getMenuCode, menuCodes);
                    List<TimerMenu> menus = timerMenuMapper.selectList(menuWrapper);
                    if (CollectionUtils.isEmpty(menus)) {
                        throw new BusinessException("获取菜单失败");
                    }
                    List<MenuTreeVO> menuTree = menus.stream()
                            .map(m -> {
                                MenuTreeVO vo = new MenuTreeVO();
                                vo.setId(m.getId());
                                vo.setParentId(m.getParentId());
                                vo.setMenuName(m.getMenuName());
                                vo.setMenuCode(m.getMenuCode());
                                vo.setMenuType(m.getMenuType());
                                vo.setRoutePath(m.getRoutePath());
                                vo.setIcon(m.getIcon());
                                vo.setSortOrder(m.getSortOrder());
                                return vo;
                            })
                            .sorted(Comparator.comparing(MenuTreeVO::getSortOrder))
                            .toList();
                    TreeFactory<Long, MenuTreeVO> treeFactory = new TreeFactory<>();
                    List<MenuTreeVO> tree = treeFactory.buildTree(menuTree);

                    return ResultVO.ok(tree);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class,
                        ex -> Mono.just(ResultVO.fail(ex.getMessage())));
    }
}