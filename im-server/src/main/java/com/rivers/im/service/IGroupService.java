package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

public interface IGroupService {

    Mono<ResultVO<MyGroupsRes>> getMyGroups(MyGroupsReq myGroupsReq);

    Mono<ResultVO<GroupMembersRes>> getGroupMembers(GroupMembersReq groupMembersReq);

    Mono<ResultVO<GroupDetailRes>> getGroupDetail(GroupDetailReq groupDetailReq);
}
