package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.DicDataReq;
import com.rivers.proto.DicDataRes;
import com.rivers.proto.SaveDicReq;
import com.rivers.proto.UpdateDicReq;
import com.rivers.user.vo.DicTreeVO;
import reactor.core.publisher.Mono;

import java.util.SequencedCollection;

public interface IDicService {

    Mono<ResultVO<Void>> saveDic(SaveDicReq saveDicReq);

    Mono<ResultVO<Void>> updateDic(UpdateDicReq updateDicReq);

    Mono<ResultVO<SequencedCollection<DicTreeVO>>> getDicTree();

    Mono<ResultVO<DicDataRes>> getDicData(DicDataReq dicDataReq);
}
