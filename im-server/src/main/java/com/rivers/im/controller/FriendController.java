package com.rivers.im.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.service.IFriendService;
import com.rivers.proto.FriendRequestPageReq;
import com.rivers.proto.FriendRequestPageRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("friend")
public class FriendController {

    private final IFriendService friendService;

    public FriendController(IFriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("getFriendRequestPage")
    public Mono<ResultVO<FriendRequestPageRes>> getFriendRequestPage(@RequestBody FriendRequestPageReq friendRequestPageReq) {
        return friendService.getFriendRequestPage(friendRequestPageReq);
    }
}
