package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.activerecord.AbstractModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserServiceImpl implements IUserService {

    private final TimerUserMapper timerUserMapper;

    private final TimerUserRoleMapper timerUserRoleMapper;

    private final TimerRoleMenuMapper timerRoleMenuMapper;

    private final TimerMenuMapper timerMenuMapper;

    public UserServiceImpl(TimerUserMapper timerUserMapper, TimerUserRoleMapper timerUserRoleMapper,
                           TimerRoleMenuMapper timerRoleMenuMapper, TimerMenuMapper timerMenuMapper) {
        this.timerUserMapper = timerUserMapper;
        this.timerUserRoleMapper = timerUserRoleMapper;
        this.timerRoleMenuMapper = timerRoleMenuMapper;
        this.timerMenuMapper = timerMenuMapper;
    }

    @Override
    public ResultVO<Void> saveUser(SaveUserReq saveUserReq) {
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

    @Override
    public ResultVO<Void> updateUser(UpdateUserReq updateUserReq) {
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
        if (Objects.nonNull(timerUser) && timerUser.getId() != id) {
            return ResultVO.fail("用户ID已存在");
        }
        LoginUser loginUser = updateUserReq.getLoginUser();
        String currentUserId = loginUser.getUserId();
        TimerUser user = new TimerUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setPhone(phone);
        user.setMail(mail);
        user.setNickname(updateUserReq.getNickname());
        user.setUserId(userId);
        user.setUpdateUser(currentUserId);
        user.updateById();
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

    @Override
    public ResultVO<UserDetailRes> getUserDetail(UserReq userReq) {
        String userId = userReq.getUserId();
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("用户ID不能为空");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        TimerUser timerUser = timerUserMapper.selectOne(userWrapper);
        if (timerUser == null) {
            return ResultVO.fail("用户不存在");
        }
        UserDetailRes userDetailRes = UserDetailRes.newBuilder()
                .setId(timerUser.getId())
                .setUserId(timerUser.getUserId())
                .setUsername(timerUser.getUsername())
                .setMail(timerUser.getMail())
                .setNickname(timerUser.getNickname())
                .setPhone(timerUser.getPhone())
                .build();
        return ResultVO.ok(userDetailRes);
    }

    @Override
    public ResultVO<Void> deleteUser(UserReq userReq) {
        String userId = userReq.getUserId();
        if (StringUtils.isBlank(userId)) {
            return ResultVO.fail("用户ID不能为空");
        }
        LambdaQueryWrapper<TimerUser> userWrapper = Wrappers.lambdaQuery();
        userWrapper.eq(TimerUser::getUserId, userId);
        TimerUser timerUser = timerUserMapper.selectOne(userWrapper);
        Optional.ofNullable(timerUser).ifPresent(AbstractModel::deleteById);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<List<MenuTreeVO>> ownedMenuTree(OwnedMenuTreeReq ownedMenuTreeReq) {
        LoginUser loginUser = ownedMenuTreeReq.getLoginUser();
        String userId = loginUser.getUserId();
        LambdaQueryWrapper<TimerUserRole> userRoleWrapper = Wrappers.lambdaQuery();
        userRoleWrapper.eq(TimerUserRole::getUserId, userId);
        List<TimerUserRole> userRoles = timerUserRoleMapper.selectList(userRoleWrapper);
        if (CollectionUtils.isEmpty(userRoles)) {
            return ResultVO.fail("获取菜单失败");
        }
        List<String> roleCodes = userRoles.stream()
                .map(TimerUserRole::getRoleCode)
                .toList();
        LambdaQueryWrapper<TimerRoleMenu> roleMenuWrapper = Wrappers.lambdaQuery();
        roleMenuWrapper.in(TimerRoleMenu::getRoleCode, roleCodes);
        List<TimerRoleMenu> roleMenus = timerRoleMenuMapper.selectList(roleMenuWrapper);
        if (CollectionUtils.isEmpty(roleMenus)) {
            return ResultVO.fail("获取菜单失败");
        }
        List<String> menuCodes = roleMenus.stream()
                .map(TimerRoleMenu::getMenuCode)
                .distinct()
                .toList();
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        menuWrapper.in(TimerMenu::getMenuCode, menuCodes);
        List<TimerMenu> menus = timerMenuMapper.selectList(menuWrapper);
        if (CollectionUtils.isEmpty(menus)) {
            return ResultVO.fail("获取菜单失败");
        }
        List<MenuTreeVO> menuTree = menus.stream()
                .map(i -> {
                    MenuTreeVO menuTreeVO = new MenuTreeVO();
                    menuTreeVO.setId(i.getId());
                    menuTreeVO.setParentId(i.getParentId());
                    menuTreeVO.setMenuName(i.getMenuName());
                    menuTreeVO.setMenuCode(i.getMenuCode());
                    menuTreeVO.setMenuType(i.getMenuType());
                    menuTreeVO.setRoutePath(i.getRoutePath());
                    menuTreeVO.setIcon(i.getIcon());
                    menuTreeVO.setMenuType(i.getMenuType());
                    menuTreeVO.setSortOrder(i.getSortOrder());
                    return menuTreeVO;
                })
                .sorted(Comparator.comparing(MenuTreeVO::getSortOrder))
                .toList();
        TreeFactory<Long, MenuTreeVO> treeFactory = new TreeFactory<>();
        List<MenuTreeVO> tree = treeFactory.buildTree(menuTree);
        return ResultVO.ok(tree);
    }
}
