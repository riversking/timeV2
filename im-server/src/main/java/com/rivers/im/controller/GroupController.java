package com.rivers.im.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.service.IGroupService;
import com.rivers.proto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public Mono<ResultVO<MyGroupsRes>> getMyGroups(@RequestBody MyGroupsReq myGroupsReq) {
        return groupService.getMyGroups(myGroupsReq);
    }


    @PostMapping("getGroupMembers")
    public Mono<ResultVO<GroupMembersRes>> getGroupMembers(@RequestBody GroupMembersReq groupMembersReq) {
        return groupService.getGroupMembers(groupMembersReq);
    }

    @PostMapping("getGroupDetail")
    public Mono<ResultVO<GroupDetailRes>> getGroupDetail(@RequestBody GroupDetailReq groupDetailReq) {
        return groupService.getGroupDetail(groupDetailReq);
    }
}
