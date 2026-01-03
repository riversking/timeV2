package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IRoleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("role")
public class RoleController {

    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("saveRole")
    public Mono<ResultVO<Void>> saveRole(@RequestBody SaveRoleReq saveRoleReq) {
        return roleService.saveRole(saveRoleReq);
    }

    @PostMapping("updateRole")
    public Mono<ResultVO<Void>> updateRole(@RequestBody UpdateRoleReq updateRoleReq) {
        return roleService.updateRole(updateRoleReq);
    }

    @PostMapping("getRolePage")
    public Mono<ResultVO<RolePageRes>> getRolePage(@RequestBody RolePageReq rolePageReq) {
        return roleService.getRolePage(rolePageReq);
    }

    @PostMapping("deleteRole")
    public Mono<ResultVO<Void>> deleteRole(@RequestBody DeleteRoleReq deleteRoleReq) {
        return roleService.deleteRole(deleteRoleReq);
    }

    @PostMapping("getRoleDetail")
    public Mono<ResultVO<RoleDetailRes>> getRoleDetail(@RequestBody RoleDetailReq roleDetailReq) {
        return roleService.getRoleDetail(roleDetailReq);
    }

    @PostMapping("saveUserRole")
    public Mono<ResultVO<Void>> saveUserRole(@RequestBody SaveUserRoleReq saveUserRoleReq) {
        return roleService.saveUserRole(saveUserRoleReq);
    }

    @PostMapping("removeUserRole")
    public Mono<ResultVO<Void>> removeUserRole(@RequestBody RemoveUserRoleReq removeUserRoleReq) {
        return roleService.removeUserRole(removeUserRoleReq);
    }

    @PostMapping("getUserRolePage")
    public Mono<ResultVO<UserRolePageRes>> getUserRolePage(@RequestBody UserRolePageReq userRolePageReq) {
        return roleService.getUserRolePage(userRolePageReq);
    }
}