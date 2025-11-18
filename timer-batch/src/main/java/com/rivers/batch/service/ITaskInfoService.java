package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.SaveTaskInfoReq;

public interface ITaskInfoService {

    ResultVO<Void> saveTaskInfo(SaveTaskInfoReq saveTaskInfoReq);
}
