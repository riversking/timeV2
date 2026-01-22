package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

public interface IRoleService {

    Mono<ResultVO<Void>> saveRole(SaveRoleReq saveRoleReq);

    Mono<ResultVO<Void>> updateRole(UpdateRoleReq updateRoleReq);

    Mono<ResultVO<RolePageRes>> getRolePage(RolePageReq rolePageReq);

    Mono<ResultVO<Void>> deleteRole(DeleteRoleReq deleteRoleReq);

    Mono<ResultVO<RoleDetailRes>> getRoleDetail(RoleDetailReq roleDetailReq);

    Mono<ResultVO<Void>> saveUserRole(SaveUserRoleReq saveUserRoleReq);

    Mono<ResultVO<Void>> removeUserRole(RemoveUserRoleReq removeUserRoleReq);

    Mono<ResultVO<RoleUserPageRes>> getUserRolePage(RoleUserPageReq roleUserPageReq);
}