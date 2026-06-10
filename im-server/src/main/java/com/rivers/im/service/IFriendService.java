package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.FriendListReq;
import com.rivers.proto.FriendListRes;
import com.rivers.proto.FriendRequestPageReq;
import com.rivers.proto.FriendRequestPageRes;
import reactor.core.publisher.Mono;

public interface IFriendService {

    Mono<ResultVO<FriendRequestPageRes>> getFriendRequestPage(FriendRequestPageReq friendRequestPageReq);

    Mono<ResultVO<FriendListRes>> getFriendList(FriendListReq friendListReq);
}
