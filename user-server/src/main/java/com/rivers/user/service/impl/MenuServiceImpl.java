package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.protobuf.ProtocolStringList;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerMenu;
import com.rivers.user.entity.TimerRole;
import com.rivers.user.entity.TimerRoleMenu;
import com.rivers.user.mapper.TimerMenuMapper;
import com.rivers.user.mapper.TimerRoleMapper;
import com.rivers.user.mapper.TimerRoleMenuMapper;
import com.rivers.user.service.IMenuService;
import com.rivers.user.vo.MenuTreeVO;
import com.rivers.user.vo.RoleMenuTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

@Service
@Slf4j
public class MenuServiceImpl implements IMenuService {

    private final TimerMenuMapper timerMenuMapper;
    private final TimerRoleMapper timerRoleMapper;
    private final TimerRoleMenuMapper timerRoleMenuMapper;

    public MenuServiceImpl(TimerMenuMapper timerMenuMapper, TimerRoleMapper timerRoleMapper,
                           TimerRoleMenuMapper timerRoleMenuMapper) {
        this.timerMenuMapper = timerMenuMapper;
        this.timerRoleMapper = timerRoleMapper;
        this.timerRoleMenuMapper = timerRoleMenuMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveMenu(SaveMenuReq saveMenuReq) {
        if (StringUtils.isBlank(saveMenuReq.getMenuCode())) {
            return Mono.just(ResultVO.fail("菜单编码不能为空"));
        }
        if (StringUtils.isBlank(saveMenuReq.getMenuName())) {
            return Mono.just(ResultVO.fail("菜单名称不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerMenu> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerMenu::getMenuCode, saveMenuReq.getMenuCode());
                    if (timerMenuMapper.selectCount(wrapper) > 0) {
                        throw new BusinessException("菜单编码已存在");
                    }
                    LoginUser user = saveMenuReq.getLoginUser();
                    TimerMenu menu = new TimerMenu();
                    menu.setMenuCode(saveMenuReq.getMenuCode());
                    menu.setMenuName(saveMenuReq.getMenuName());
                    menu.setMenuType(saveMenuReq.getMenuType());
                    menu.setIcon(saveMenuReq.getIcon());
                    menu.setParentId(saveMenuReq.getParentId() == 0 ? -1L : saveMenuReq.getParentId());
                    menu.setRoutePath(saveMenuReq.getRoutePath());
                    menu.setPermissionCode(saveMenuReq.getPermissionCode());
                    menu.setSortOrder(saveMenuReq.getSortOrder());
                    menu.setCreateUser(user.getUserId());
                    menu.setUpdateUser(user.getUserId());
                    timerMenuMapper.insert(menu);
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("保存菜单失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> updateMenu(UpdateMenuReq updateMenuReq) {
        if (StringUtils.isBlank(updateMenuReq.getMenuCode()) || StringUtils.isBlank(updateMenuReq.getMenuName())) {
            return Mono.just(ResultVO.fail("菜单编码和名称不能为空"));
        }
        return Mono.fromCallable(() -> {
                    long id = updateMenuReq.getId();
                    LambdaQueryWrapper<TimerMenu> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerMenu::getMenuCode, updateMenuReq.getMenuCode());
                    TimerMenu existing = timerMenuMapper.selectOne(wrapper);
                    if (existing != null && !Objects.equals(existing.getId(), id)) {
                        throw new BusinessException("菜单编码已存在");
                    }
                    TimerMenu menu = new TimerMenu();
                    menu.setId(id);
                    menu.setMenuCode(updateMenuReq.getMenuCode());
                    menu.setMenuName(updateMenuReq.getMenuName());
                    menu.setMenuType(updateMenuReq.getMenuType());
                    menu.setIcon(updateMenuReq.getIcon());
                    menu.setParentId(updateMenuReq.getParentId());
                    menu.setRoutePath(updateMenuReq.getRoutePath());
                    menu.setPermissionCode(updateMenuReq.getPermissionCode());
                    menu.setSortOrder(updateMenuReq.getSortOrder());
                    menu.setUpdateUser(updateMenuReq.getLoginUser().getUserId());
                    menu.updateById();
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新菜单失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<List<MenuTreeVO>>> getMenuTree() {
        return Mono.fromCallable(() -> {
                    List<TimerMenu> menus = timerMenuMapper.selectList(Wrappers.emptyWrapper());
                    List<MenuTreeVO> vos = menus.stream()
                            .map(menu -> {
                                MenuTreeVO vo = new MenuTreeVO();
                                BeanUtils.copyProperties(menu, vo);
                                return vo;
                            })
                            .toList();

                    TreeFactory<Long, MenuTreeVO> factory = new TreeFactory<>();
                    List<MenuTreeVO> tree = factory.buildTree(vos);
                    return ResultVO.ok(tree);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("获取菜单树失败", e);
                    return Mono.just(ResultVO.fail("加载菜单失败"));
                });
    }

    @Override
    public Mono<ResultVO<MenuDetailRes>> getMenuDetail(MenuDetailReq menuDetailReq) {
        if (StringUtils.isBlank(menuDetailReq.getMenuCode())) {
            return Mono.just(ResultVO.fail("菜单编码不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerMenu> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerMenu::getMenuCode, menuDetailReq.getMenuCode());
                    TimerMenu menu = timerMenuMapper.selectOne(wrapper);
                    MenuDetailRes res = Optional.ofNullable(menu)
                            .map(m -> MenuDetailRes.newBuilder()
                                    .setId(m.getId())
                                    .setMenuCode(m.getMenuCode())
                                    .setMenuName(m.getMenuName())
                                    .setMenuType(m.getMenuType())
                                    .setIcon(m.getIcon())
                                    .setParentId(m.getParentId())
                                    .setRoutePath(m.getRoutePath())
                                    .setSortOrder(m.getSortOrder())
                                    .setPermissionCode(m.getPermissionCode())
                                    .build())
                            .orElse(null);

                    return ResultVO.ok(res);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("查询菜单详情失败", e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteMenu(DeleteMenuReq deleteMenuReq) {
        if (StringUtils.isBlank(deleteMenuReq.getMenuCode())) {
            return Mono.just(ResultVO.fail("菜单编码不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerMenu> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerMenu::getMenuCode, deleteMenuReq.getMenuCode());
                    TimerMenu menu = timerMenuMapper.selectOne(wrapper);
                    if (menu != null) {
                        menu.deleteById();
                    }
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("删除菜单失败", e);
                    return Mono.just(ResultVO.fail("删除失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> saveRoleMenu(SaveRoleMenuReq saveRoleMenuReq) {
        String roleCode = saveRoleMenuReq.getRoleCode();
        ProtocolStringList menuCodes = saveRoleMenuReq.getMenuCodesList();
        if (StringUtils.isBlank(roleCode)) {
            return Mono.just(ResultVO.fail("角色编码不能为空"));
        }
        if (CollectionUtils.isEmpty(menuCodes)) {
            return Mono.just(ResultVO.fail("菜单编码不能为空"));
        }
        return Mono.fromCallable(() -> {
                    // 检查角色是否存在
                    LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
                    roleWrapper.eq(TimerRole::getRoleCode, roleCode);
                    TimerRole role = timerRoleMapper.selectOne(roleWrapper);
                    if (role == null) {
                        throw new BusinessException("角色不存在");
                    }
                    // 获取有效菜单
                    LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
                    menuWrapper.in(TimerMenu::getMenuCode, menuCodes);
                    List<TimerMenu> validMenus = timerMenuMapper.selectList(menuWrapper);
                    if (CollectionUtils.isEmpty(validMenus)) {
                        return ResultVO.<Void>ok();
                    }
                    List<String> validMenuCodes = validMenus.stream()
                            .map(TimerMenu::getMenuCode)
                            .distinct()
                            .toList();
                    // 查询已存在的关联
                    LambdaQueryWrapper<TimerRoleMenu> existWrapper = Wrappers.lambdaQuery();
                    existWrapper.eq(TimerRoleMenu::getRoleCode, roleCode)
                            .in(TimerRoleMenu::getMenuCode, validMenuCodes);
                    List<TimerRoleMenu> exists = timerRoleMenuMapper.selectList(existWrapper);
                    List<String> existCodes = exists.stream()
                            .map(TimerRoleMenu::getMenuCode)
                            .toList();
                    // 构建新记录
                    var loginUser = saveRoleMenuReq.getLoginUser();
                    String userId = loginUser.getUserId();
                    List<TimerRoleMenu> toInsert = validMenuCodes.stream()
                            .filter(code -> !existCodes.contains(code))
                            .map(code -> {
                                TimerRoleMenu rm = new TimerRoleMenu();
                                rm.setRoleCode(roleCode);
                                rm.setMenuCode(code);
                                rm.setCreateUser(userId);
                                rm.setUpdateUser(userId);
                                return rm;
                            })
                            .toList();

                    if (!toInsert.isEmpty()) {
                        timerRoleMenuMapper.insert(toInsert);
                    }
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e -> Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("保存角色菜单失败", e);
                    return Mono.just(ResultVO.fail("操作失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> removeRoleMenu(RemoveRoleMenuReq removeRoleMenuReq) {
        String roleCode = removeRoleMenuReq.getRoleCode();
        ProtocolStringList menuCodes = removeRoleMenuReq.getMenuCodesList();
        if (StringUtils.isBlank(roleCode)) {
            return Mono.just(ResultVO.fail("角色编码不能为空"));
        }
        if (CollectionUtils.isEmpty(menuCodes)) {
            return Mono.just(ResultVO.fail("菜单编码不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerRoleMenu> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(TimerRoleMenu::getRoleCode, roleCode)
                            .in(TimerRoleMenu::getMenuCode, menuCodes);
                    timerRoleMenuMapper.delete(wrapper);
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("移除角色菜单失败", e);
                    return Mono.just(ResultVO.fail("操作失败"));
                });
    }

    @Override
    public Mono<ResultVO<SequencedCollection<RoleMenuTreeVO>>> getRoleMenuTree(RoleMenuTreeReq roleMenuTreeReq) {
        String roleCode = roleMenuTreeReq.getRoleCode();
        if (StringUtils.isBlank(roleCode)) {
            return Mono.just(ResultVO.fail("角色编码不能为空"));
        }
        return Mono.fromCallable(() -> {
                    // 验证角色存在
                    LambdaQueryWrapper<TimerRole> roleWrapper = Wrappers.lambdaQuery();
                    roleWrapper.eq(TimerRole::getRoleCode, roleCode);
                    if (timerRoleMapper.selectCount(roleWrapper) == 0) {
                        throw new BusinessException("角色不存在");
                    }
                    // 获取该角色已分配的菜单
                    LambdaQueryWrapper<TimerRoleMenu> rmWrapper = Wrappers.lambdaQuery();
                    rmWrapper.eq(TimerRoleMenu::getRoleCode, roleCode);
                    List<String> assignedMenuCodes = timerRoleMenuMapper.selectList(rmWrapper)
                            .stream()
                            .map(TimerRoleMenu::getMenuCode)
                            .toList();
                    // 获取所有菜单
                    List<TimerMenu> allMenus = timerMenuMapper.selectList(Wrappers.emptyWrapper());
                    // 构建带 checked 的 VO
                    List<RoleMenuTreeVO> vos = allMenus.stream()
                            .map(menu -> {
                                RoleMenuTreeVO vo = new RoleMenuTreeVO();
                                vo.setId(menu.getId());
                                vo.setParentId(menu.getParentId());
                                vo.setMenuName(menu.getMenuName());
                                vo.setMenuCode(menu.getMenuCode());
                                vo.setSortOrder(menu.getSortOrder());
                                vo.setChecked(assignedMenuCodes.contains(menu.getMenuCode()));
                                return vo;
                            })
                            .toList();
                    TreeFactory<Long, RoleMenuTreeVO> factory = new TreeFactory<>();
                    SequencedCollection<RoleMenuTreeVO> tree = factory.buildTreeOrdered(vos);
                    return ResultVO.ok(tree);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())));
    }
}