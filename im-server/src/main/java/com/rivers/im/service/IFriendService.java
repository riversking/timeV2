package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.SaveFriendReq;
import reactor.core.publisher.Mono;

public interface IFriendService {

    Mono<ResultVO<Void>> saveFriend(SaveFriendReq saveFriendReq);
}
