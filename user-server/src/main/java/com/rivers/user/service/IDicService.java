package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.SaveDicReq;
import com.rivers.proto.UpdateDicReq;
import com.rivers.user.vo.DicTreeVO;

import java.util.List;
import java.util.SequencedCollection;

public interface IDicService {

    ResultVO<Void> saveDic(SaveDicReq saveDicReq);

    ResultVO<Void> updateDic(UpdateDicReq updateDicReq);

    ResultVO<SequencedCollection<DicTreeVO>> getDicTree();
}
