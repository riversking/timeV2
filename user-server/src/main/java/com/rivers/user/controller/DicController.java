package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.DicDataReq;
import com.rivers.proto.DicDataRes;
import com.rivers.proto.SaveDicReq;
import com.rivers.proto.UpdateDicReq;
import com.rivers.user.service.IDicService;
import com.rivers.user.vo.DicTreeVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.SequencedCollection;

@RestController
@RequestMapping("dic")
public class DicController {

    private final IDicService dicService;

    public DicController(IDicService dicService) {
        this.dicService = dicService;
    }

    @PostMapping("saveDic")
    public ResultVO<Void> saveDic(@RequestBody SaveDicReq saveDicReq) {
        return dicService.saveDic(saveDicReq);
    }

    @PostMapping("updateDic")
    public ResultVO<Void> updateDic(@RequestBody UpdateDicReq updateDicReq) {
        return dicService.updateDic(updateDicReq);
    }

    @PostMapping("getDicTree")
    public ResultVO<SequencedCollection<DicTreeVO>> getDicTree() {
        return dicService.getDicTree();
    }

    @PostMapping("getDicData")
    public ResultVO<DicDataRes> getDicData(@RequestBody DicDataReq dicDataReq) {
        return dicService.getDicData(dicDataReq);
    }
}

