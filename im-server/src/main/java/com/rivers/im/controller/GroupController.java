package com.rivers.im.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.service.IGroupService;
import com.rivers.proto.GroupMembersReq;
import com.rivers.proto.GroupMembersRes;
import com.rivers.proto.MyGroupsReq;
import com.rivers.proto.MyGroupsRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("group")
public class GroupController {

    private final IGroupService groupService;

    public GroupController(IGroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping("getMyGroups")
    public Mono<ResultVO<MyGroupsRes>> getMyGroups(MyGroupsReq myGroupsReq) {
        return groupService.getMyGroups(myGroupsReq);
    }


    @PostMapping("getGroupMembers")
    public Mono<ResultVO<GroupMembersRes>> getGroupMembers(GroupMembersReq groupMembersReq) {
        return groupService.getGroupMembers(groupMembersReq);
    }
}
