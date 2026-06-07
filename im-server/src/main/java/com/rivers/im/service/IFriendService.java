package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.FriendRequestPageReq;
import com.rivers.proto.FriendRequestPageRes;
import reactor.core.publisher.Mono;

public interface IFriendService {

    Mono<ResultVO<FriendRequestPageRes>> getFriendRequestPage(FriendRequestPageReq friendRequestPageReq);
}
