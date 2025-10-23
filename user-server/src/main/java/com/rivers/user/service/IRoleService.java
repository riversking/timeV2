package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;

public interface IRoleService {

    ResultVO<Void> saveRole(SaveRoleReq saveRoleReq);

    ResultVO<Void> updateRole(UpdateRoleReq updateRoleReq);

    ResultVO<RolePageRes> getRolePage(RolePageReq rolePageReq);

    ResultVO<Void> deleteRole(DeleteRoleReq deleteRoleReq);

    ResultVO<RoleDetailRes> getRoleDetail(RoleDetailReq roleDetailReq);

    ResultVO<Void> saveUserRole(SaveUserRoleReq saveUserRoleReq);

    ResultVO<Void> removeUserRole(RemoveUserRoleReq removeUserRoleReq);
}
