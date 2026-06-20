package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.GroupMembersReq;
import com.rivers.proto.GroupMembersRes;
import com.rivers.proto.MyGroupsReq;
import com.rivers.proto.MyGroupsRes;
import reactor.core.publisher.Mono;

public interface IGroupService {

    Mono<ResultVO<MyGroupsRes>> getMyGroups(MyGroupsReq myGroupsReq);

    Mono<ResultVO<GroupMembersRes>> getGroupMembers(GroupMembersReq groupMembersReq);
}
