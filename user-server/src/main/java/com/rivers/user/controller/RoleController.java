package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IRoleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("role")
public class RoleController {

    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("saveRole")
    public ResultVO<Void> saveRole(@RequestBody SaveRoleReq saveRoleReq) {
        return roleService.saveRole(saveRoleReq);
    }

    @PostMapping("updateRole")
    public ResultVO<Void> updateRole(@RequestBody UpdateRoleReq updateRoleReq) {
        return roleService.updateRole(updateRoleReq);
    }

    @PostMapping("getRolePage")
    public ResultVO<RolePageRes> getRolePage(@RequestBody RolePageReq rolePageReq) {
        return roleService.getRolePage(rolePageReq);
    }

    @PostMapping("deleteRole")
    public ResultVO<Void> deleteRole(@RequestBody DeleteRoleReq deleteRoleReq) {
        return roleService.deleteRole(deleteRoleReq);
    }

    @PostMapping("getRoleDetail")
    public ResultVO<RoleDetailRes> getRoleDetail(@RequestBody RoleDetailReq roleDetailReq) {
        return roleService.getRoleDetail(roleDetailReq);
    }

    @PostMapping("saveUserRole")
    public ResultVO<Void> saveUserRole(@RequestBody SaveUserRoleReq saveUserRoleReq) {
        return roleService.saveUserRole(saveUserRoleReq);
    }

    @PostMapping("removeUserRole")
    public ResultVO<Void> removeUserRole(@RequestBody RemoveUserRoleReq removeUserRoleReq) {
        return roleService.removeUserRole(removeUserRoleReq);
    }
}
