package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerDic;
import com.rivers.user.mapper.TimerDicMapper;
import com.rivers.user.service.IDicService;
import com.rivers.user.vo.DicTreeVO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.SequencedCollection;

@Service
@Slf4j
public class DicServiceImpl implements IDicService {

    private final TimerDicMapper timerDicMapper;

    public DicServiceImpl(TimerDicMapper timerDicMapper) {
        this.timerDicMapper = timerDicMapper;
    }

    @Override
    public ResultVO<Void> saveDic(SaveDicReq saveDicReq) {
        String dicKey = saveDicReq.getDicKey();
        String dicValue = saveDicReq.getDicValue();
        String dicDesc = saveDicReq.getDicDesc();
        long parentId = saveDicReq.getParentId();
        int sort = saveDicReq.getSort();
        if (StringUtils.isBlank(dicKey)) {
            return ResultVO.fail("字典key不能为空");
        }
        if (StringUtils.isBlank(dicValue)) {
            return ResultVO.fail("字典值不能为空");
        }
        LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
        dicWrapper.eq(TimerDic::getDicKey, dicKey);
        long count = timerDicMapper.selectCount(dicWrapper);
        if (count > 0) {
            return ResultVO.fail("字典已存在");
        }
        LoginUser loginUser = saveDicReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerDic timerDic = new TimerDic();
        timerDic.setDicKey(dicKey);
        timerDic.setDicValue(dicValue);
        timerDic.setDicDesc(dicDesc);
        timerDic.setParentId(parentId == 0 ? -1L : parentId);
        timerDic.setSort(sort);
        timerDic.setCreateUser(userId);
        timerDic.setUpdateUser(userId);
        timerDic.insert();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> updateDic(UpdateDicReq updateDicReq) {
        String dicKey = updateDicReq.getDicKey();
        String dicValue = updateDicReq.getDicValue();
        String dicDesc = updateDicReq.getDicDesc();
        long parentId = updateDicReq.getParentId();
        int sort = updateDicReq.getSort();
        if (StringUtils.isBlank(dicKey)) {
            return ResultVO.fail("字典key不能为空");
        }
        if (StringUtils.isBlank(dicValue)) {
            return ResultVO.fail("字典值不能为空");
        }
        long id = updateDicReq.getId();
        LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
        dicWrapper.eq(TimerDic::getDicKey, dicKey);
        TimerDic timerDic = timerDicMapper.selectOne(dicWrapper);
        if (timerDic != null && timerDic.getId() != id) {
            return ResultVO.fail("字典已存在");
        }
        TimerDic dic = new TimerDic();
        dic.setId(id);
        dic.setDicKey(dicKey);
        dic.setDicValue(dicValue);
        dic.setDicDesc(dicDesc);
        dic.setParentId(parentId);
        dic.setSort(sort);
        dic.updateById();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<SequencedCollection<DicTreeVO>> getDicTree() {
        LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
        dicWrapper.orderByAsc(TimerDic::getSort)
                .select(TimerDic::getId, TimerDic::getDicKey, TimerDic::getParentId);
        long startTime = System.currentTimeMillis();
        List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
        log.info("timerDictionaries: {}", System.currentTimeMillis() - startTime);
        List<DicTreeVO> dicTrees = timerDictionaries.stream()
                .map(i -> {
                    DicTreeVO dicTreeVO = new DicTreeVO();
                    dicTreeVO.setId(i.getId());
                    dicTreeVO.setDicKey(i.getDicKey());
                    dicTreeVO.setDicValue(i.getDicValue());
                    dicTreeVO.setSort(i.getSort());
                    dicTreeVO.setParentId(i.getParentId());
                    return dicTreeVO;
                })
                .toList();
        TreeFactory<Long, DicTreeVO> treeFactory = new TreeFactory<>();
        SequencedCollection<DicTreeVO> tree = treeFactory.buildTreeOrdered(dicTrees);
        return ResultVO.ok(tree);
    }

    @Override
    public ResultVO<DicDataRes> getDicData(DicDataReq dicDataReq) {
        var dicKey = dicDataReq.getDicKey();
        if (StringUtils.isNotBlank(dicKey)) {
            return ResultVO.fail("字典key不能为空");
        }
        LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
        dicWrapper.eq(StringUtils.isNotBlank(dicKey), TimerDic::getDicKey, dicKey)
                .orderByAsc(TimerDic::getSort)
                .select(TimerDic::getId,TimerDic::getParentId, TimerDic::getDicKey, TimerDic::getDicValue, TimerDic::getSort);
        List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
        List<Dic> dicList = timerDictionaries.stream()
                .map(i -> Dic.newBuilder()
                        .setId(i.getId())
                        .setDicKey(i.getDicKey())
                        .setDicValue(i.getDicValue())
                        .setParentId(i.getParentId())
                        .build())
                .toList();
        return ResultVO.ok(DicDataRes.newBuilder().addAllDicData(dicList).build());
    }
}
