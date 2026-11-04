package com.rivers.user.service.impl;

import com.google.protobuf.ProtocolStringList;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerMenu;
import com.rivers.user.entity.TimerRoleMenu;
import com.rivers.user.repository.TimerMenuRepository;
import com.rivers.user.repository.TimerRoleMenuRepository;
import com.rivers.user.repository.TimerRoleRepository;
import com.rivers.user.service.IMenuService;
import com.rivers.user.vo.MenuTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class MenuServiceImpl implements IMenuService {

    public static final String MENU_EMPTY = "菜单编码不能为空";
    public static final String ROLE_EMPTY = "角色编码不能为空";
    /**
     * 菜单归属：1-后台管理，2-门户
     */
    private static final int SCOPE_ADMIN = 1;
    private static final int SCOPE_PORTAL = 2;
    private static final int STATUS_ENABLED = 1;
    private static final Integer NOT_DELETED = 0;

    private final TimerMenuRepository timerMenuRepository;
    private final TimerRoleRepository timerRoleRepository;
    private final TimerRoleMenuRepository timerRoleMenuRepository;
    private final TransactionalOperator transactionalOperator;

    public MenuServiceImpl(TimerMenuRepository timerMenuRepository,
                           TimerRoleRepository timerRoleRepository,
                           TimerRoleMenuRepository timerRoleMenuRepository,
                           TransactionalOperator transactionalOperator) {
        this.timerMenuRepository = timerMenuRepository;
        this.timerRoleRepository = timerRoleRepository;
        this.timerRoleMenuRepository = timerRoleMenuRepository;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<ResultVO<Void>> saveMenu(SaveMenuReq saveMenuReq) {
        if (StringUtils.isBlank(saveMenuReq.getMenuCode())) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        if (StringUtils.isBlank(saveMenuReq.getMenuName())) {
            return Mono.just(ResultVO.fail("菜单名称不能为空"));
        }
        String menuCode = saveMenuReq.getMenuCode();
        String currentUserId = saveMenuReq.getLoginUser().getUserId();
        return timerMenuRepository.existsByMenuCodeAndIsDeleted(menuCode, NOT_DELETED)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.just(ResultVO.<Void>fail("菜单编码已存在"));
                    }
                    TimerMenu menu = new TimerMenu();
                    menu.setMenuCode(menuCode);
                    menu.setMenuName(saveMenuReq.getMenuName());
                    menu.setMenuType(saveMenuReq.getMenuType());
                    menu.setMenuScope(normalizeScope(saveMenuReq.getMenuScope()));
                    menu.setIcon(saveMenuReq.getIcon());
                    menu.setParentId(saveMenuReq.getParentId() == 0 ? -1L : saveMenuReq.getParentId());
                    menu.setRoutePath(saveMenuReq.getRoutePath());
                    menu.setPermissionCode(saveMenuReq.getPermissionCode());
                    menu.setComponent(saveMenuReq.getComponent());
                    menu.setSortOrder(saveMenuReq.getSortOrder());
                    menu.setCreateUser(currentUserId);
                    menu.setUpdateUser(currentUserId);
                    menu.setCreateTime(LocalDateTime.now());
                    menu.setUpdateTime(LocalDateTime.now());
                    return timerMenuRepository.save(menu).thenReturn(ResultVO.<Void>ok());
                })
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
        long id = updateMenuReq.getId();
        String menuCode = updateMenuReq.getMenuCode();
        String currentUserId = updateMenuReq.getLoginUser().getUserId();
        return timerMenuRepository.findByMenuCodeAndIsDeleted(menuCode, NOT_DELETED)
                .flatMap(existing -> {
                    if (!existing.getId().equals(id)) {
                        return Mono.just(ResultVO.<Void>fail("菜单编码已存在"));
                    }
                    return doUpdateMenu(id, updateMenuReq, currentUserId);
                })
                .switchIfEmpty(Mono.defer(() -> doUpdateMenu(id, updateMenuReq, currentUserId)))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新菜单失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private Mono<ResultVO<Void>> doUpdateMenu(long id, UpdateMenuReq updateMenuReq, String currentUserId) {
        return timerMenuRepository.updateMenu(id, updateMenuReq.getMenuCode(), updateMenuReq.getMenuName(),
                        updateMenuReq.getMenuType(), normalizeScope(updateMenuReq.getMenuScope()),
                        updateMenuReq.getIcon(), updateMenuReq.getParentId(), updateMenuReq.getRoutePath(),
                        updateMenuReq.getPermissionCode(), updateMenuReq.getComponent(),
                        updateMenuReq.getSortOrder(), currentUserId)
                .map(rows -> ResultVO.<Void>ok());
    }

    @Override
    public Mono<ResultVO<List<MenuTreeVO>>> getMenuTree() {
        return timerMenuRepository.findAllByIsDeleted(NOT_DELETED)
                .map(this::toMenuTreeVO)
                .collectList()
                .map(menuTrees -> {
                    menuTrees.sort(Comparator.comparing(MenuTreeVO::getSortOrder));
                    TreeFactory<Long, MenuTreeVO> factory = new TreeFactory<>();
                    return ResultVO.ok(factory.buildTree(menuTrees));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("获取菜单树失败", e);
                    return Mono.just(ResultVO.fail("加载菜单失败"));
                });
    }

    @Override
    public Mono<ResultVO<List<MenuTreeVO>>> getPortalMenuTree() {
        return timerMenuRepository.findPortalMenus(SCOPE_PORTAL, STATUS_ENABLED)
                .map(this::toMenuTreeVO)
                .collectList()
                .map(ResultVO::ok)
                .onErrorResume(Exception.class, e -> {
                    log.error("获取门户菜单失败", e);
                    return Mono.just(ResultVO.fail("加载菜单失败"));
                });
    }

    @Override
    public Mono<ResultVO<MenuDetailRes>> getMenuDetail(MenuDetailReq menuDetailReq) {
        if (StringUtils.isBlank(menuDetailReq.getMenuCode())) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        return timerMenuRepository.findByMenuCodeAndIsDeleted(menuDetailReq.getMenuCode(), NOT_DELETED)
                .map(menu -> ResultVO.ok(MenuDetailRes.newBuilder()
                        .setId(menu.getId())
                        .setMenuCode(menu.getMenuCode())
                        .setMenuName(menu.getMenuName())
                        .setMenuType(menu.getMenuType())
                        .setIcon(menu.getIcon())
                        .setParentId(menu.getParentId())
                        .setRoutePath(menu.getRoutePath())
                        .setSortOrder(menu.getSortOrder())
                        .setPermissionCode(menu.getPermissionCode())
                        .build()))
                .defaultIfEmpty(ResultVO.ok(MenuDetailRes.newBuilder().build()))
                .onErrorResume(Exception.class, e -> {
                    log.error("查询菜单详情失败", e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteMenu(DeleteMenuReq deleteMenuReq) {
        if (StringUtils.isBlank(deleteMenuReq.getMenuCode())) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        return timerMenuRepository.softDeleteByMenuCode(deleteMenuReq.getMenuCode())
                .thenReturn(ResultVO.<Void>ok())
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
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(menuCodes)) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        String currentUserId = saveRoleMenuReq.getLoginUser().getUserId();
        Mono<ResultVO<Void>> replace = timerRoleRepository.existsByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(roleExists -> {
                    if (!Boolean.TRUE.equals(roleExists)) {
                        return Mono.just(ResultVO.<Void>fail("角色不存在"));
                    }
                    return timerMenuRepository.findByMenuCodeInAndIsDeleted(menuCodes, NOT_DELETED)
                            .map(TimerMenu::getMenuCode)
                            .distinct()
                            .collectList()
                            .flatMap(validMenuCodes -> {
                                if (CollectionUtils.isEmpty(validMenuCodes)) {
                                    return Mono.just(ResultVO.<Void>ok());
                                }
                                return timerRoleMenuRepository.softDeleteByRoleCode(roleCode)
                                        .thenMany(timerRoleMenuRepository.saveAll(
                                                buildRoleMenus(roleCode, validMenuCodes, currentUserId)))
                                        .then(Mono.just(ResultVO.<Void>ok()));
                            });
                });
        return transactionalOperator.transactional(replace)
                .onErrorResume(Exception.class, e -> {
                    log.error("保存角色菜单失败", e);
                    return Mono.just(ResultVO.fail("操作失败"));
                });
    }

    private List<TimerRoleMenu> buildRoleMenus(String roleCode, List<String> menuCodes, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        return menuCodes.stream()
                .map(code -> {
                    TimerRoleMenu rm = new TimerRoleMenu();
                    rm.setRoleCode(roleCode);
                    rm.setMenuCode(code);
                    rm.setCreateUser(currentUserId);
                    rm.setUpdateUser(currentUserId);
                    rm.setCreateTime(now);
                    rm.setUpdateTime(now);
                    return rm;
                })
                .toList();
    }

    @Override
    public Mono<ResultVO<Void>> removeRoleMenu(RemoveRoleMenuReq removeRoleMenuReq) {
        String roleCode = removeRoleMenuReq.getRoleCode();
        ProtocolStringList menuCodes = removeRoleMenuReq.getMenuCodesList();
        if (StringUtils.isBlank(roleCode)) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        if (CollectionUtils.isEmpty(menuCodes)) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        return timerRoleMenuRepository.softDeleteByRoleCodeAndMenuCodeIn(roleCode, menuCodes)
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(Exception.class, e -> {
                    log.error("移除角色菜单失败", e);
                    return Mono.just(ResultVO.fail("操作失败"));
                });
    }

    @Override
    public Mono<ResultVO<CheckedMenuRes>> getRoleMenu(CheckedMenuReq checkedMenuReq) {
        String roleCode = checkedMenuReq.getRoleCode();
        if (StringUtils.isBlank(roleCode)) {
            return Mono.just(ResultVO.fail(ROLE_EMPTY));
        }
        return timerRoleRepository.existsByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                .flatMap(roleExists -> {
                    if (!Boolean.TRUE.equals(roleExists)) {
                        return Mono.just(ResultVO.<CheckedMenuRes>fail("角色不存在"));
                    }
                    return timerRoleMenuRepository.findByRoleCodeAndIsDeleted(roleCode, NOT_DELETED)
                            .map(TimerRoleMenu::getMenuCode)
                            .collectList()
                            .map(menuCodes -> ResultVO.ok(CheckedMenuRes.newBuilder()
                                    .addAllCheckedMenu(menuCodes)
                                    .build()));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("查询角色菜单失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteMenus(DeleteMenusReq deleteMenusReq) {
        ProtocolStringList menuCodesList = deleteMenusReq.getMenuCodesList();
        if (CollectionUtils.isEmpty(menuCodesList)) {
            return Mono.just(ResultVO.fail(MENU_EMPTY));
        }
        return timerMenuRepository.softDeleteByMenuCodeIn(menuCodesList)
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(Exception.class, e -> {
                    log.error("批量删除菜单失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private MenuTreeVO toMenuTreeVO(TimerMenu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        BeanUtils.copyProperties(menu, vo);
        return vo;
    }

    /**
     * 归一化菜单归属：仅 2 视为门户，其余一律后台
     */
    private static int normalizeScope(int scope) {
        return scope == SCOPE_PORTAL ? SCOPE_PORTAL : SCOPE_ADMIN;
    }
}
