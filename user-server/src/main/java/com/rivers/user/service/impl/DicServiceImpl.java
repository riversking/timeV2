package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerDic;
import com.rivers.user.mapper.TimerDicMapper;
import com.rivers.user.service.IDicService;
import com.rivers.user.vo.DicTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.SequencedCollection;

@Service
@Slf4j
public class DicServiceImpl implements IDicService {

    private final TimerDicMapper timerDicMapper;

    public DicServiceImpl(TimerDicMapper timerDicMapper) {
        this.timerDicMapper = timerDicMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveDic(SaveDicReq saveDicReq) {
        // 参数校验（非阻塞，可提前返回）
        if (StringUtils.isBlank(saveDicReq.getDicKey())) {
            return Mono.just(ResultVO.fail("字典key不能为空"));
        }
        if (StringUtils.isBlank(saveDicReq.getDicValue())) {
            return Mono.just(ResultVO.fail("字典值不能为空"));
        }
        return Mono.fromCallable(() -> {
                    String dicKey = saveDicReq.getDicKey();
                    String dicValue = saveDicReq.getDicValue();
                    String dicDesc = saveDicReq.getDicDesc();
                    long parentId = saveDicReq.getParentId();
                    int sort = saveDicReq.getSort();
                    LoginUser loginUser = saveDicReq.getLoginUser();
                    String userId = loginUser.getUserId();
                    // 检查是否已存在
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey);
                    long count = timerDicMapper.selectCount(dicWrapper);
                    if (count > 0) {
                        throw new BusinessException("字典已存在");
                    }
                    // 插入新记录
                    TimerDic timerDic = new TimerDic();
                    timerDic.setDicKey(dicKey);
                    timerDic.setDicValue(dicValue);
                    timerDic.setDicDesc(dicDesc);
                    timerDic.setParentId(parentId == 0 ? -1L : parentId);
                    timerDic.setSort(sort);
                    timerDic.setCreateUser(userId);
                    timerDic.setUpdateUser(userId);
                    timerDic.insert(); // 阻塞操
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("保存字典失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> updateDic(UpdateDicReq updateDicReq) {
        if (StringUtils.isBlank(updateDicReq.getDicKey())) {
            return Mono.just(ResultVO.fail("字典key不能为空"));
        }
        if (StringUtils.isBlank(updateDicReq.getDicValue())) {
            return Mono.just(ResultVO.fail("字典值不能为空"));
        }
        return Mono.fromCallable(() -> {
                    long id = updateDicReq.getId();
                    String dicKey = updateDicReq.getDicKey();
                    String dicValue = updateDicReq.getDicValue();
                    String dicDesc = updateDicReq.getDicDesc();
                    long parentId = updateDicReq.getParentId();
                    int sort = updateDicReq.getSort();
                    // 检查唯一性
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey);
                    TimerDic existing = timerDicMapper.selectOne(dicWrapper);
                    if (existing != null && existing.getId() != id) {
                        throw new BusinessException("字典已存在");
                    }
                    // 更新
                    TimerDic dic = new TimerDic();
                    dic.setId(id);
                    dic.setDicKey(dicKey);
                    dic.setDicValue(dicValue);
                    dic.setDicDesc(dicDesc);
                    dic.setParentId(parentId);
                    dic.setSort(sort);
                    dic.updateById(); // 阻塞
                    return ResultVO.<Void>ok();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(BusinessException.class, e ->
                        Mono.just(ResultVO.fail(e.getMessage())))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新字典失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    @Override
    public Mono<ResultVO<SequencedCollection<DicTreeVO>>> getDicTree() {
        return Mono.fromCallable(() -> {
                    long startTime = System.currentTimeMillis();
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.orderByAsc(TimerDic::getSort)
                            .select(TimerDic::getId, TimerDic::getDicKey, TimerDic::getParentId,
                                    TimerDic::getDicValue, TimerDic::getSort);
                    List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
                    log.info("查询字典树耗时: {} ms", System.currentTimeMillis() - startTime);
                    List<DicTreeVO> dicTrees = timerDictionaries.stream()
                            .map(i -> {
                                DicTreeVO vo = new DicTreeVO();
                                vo.setId(i.getId());
                                vo.setDicKey(i.getDicKey());
                                vo.setDicValue(i.getDicValue());
                                vo.setSort(i.getSort());
                                vo.setParentId(i.getParentId());
                                return vo;
                            })
                            .toList();
                    TreeFactory<Long, DicTreeVO> treeFactory = new TreeFactory<>();
                    SequencedCollection<DicTreeVO> tree = treeFactory.buildTreeOrdered(dicTrees);
                    return ResultVO.ok(tree);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("构建字典树失败", e);
                    return Mono.just(ResultVO.fail("加载字典失败"));
                });
    }

    @Override
    public Mono<ResultVO<DicDataRes>> getDicData(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        // ⚠️ 原逻辑有 bug：应该是 isBlank 才报错！
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail("字典key不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey)
                            .orderByAsc(TimerDic::getSort)
                            .select(TimerDic::getId, TimerDic::getParentId,
                                    TimerDic::getDicKey, TimerDic::getDicValue, TimerDic::getSort);
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
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("查询字典数据失败", e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    @Override
    public Mono<ResultVO<DicDataDetailRes>> getDicDataDetail(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail("字典key不能为空"));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey);
                    TimerDic dic = timerDicMapper.selectOne(dicWrapper);
                    if (dic == null) {
                        throw new BusinessException("字典不存在");
                    }
                    Long id = dic.getId();
                    dicWrapper.clear();
                    dicWrapper.eq(TimerDic::getParentId, id);
                    List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    List<DicDataDetailRes> list = timerDictionaries.stream()
                            .map(i ->
                                    DicDataDetailRes.newBuilder()
                                            .setId(i.getId())
                                            .setDicKey(i.getDicKey())
                                            .setDicValue(i.getDicValue())
                                            .setDicDesc(i.getDicDesc())
                                            .setSort(i.getSort())
                                            .setParentId(i.getParentId())
                                            .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                                    .map(dateTimeFormatter::format)
                                                    .orElse(""))
                                            .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                                    .map(dateTimeFormatter::format)
                                                    .orElse(""))
                                            .build())
                            .toList();
                    LocalDateTime createTime = dic.getCreateTime();
                    LocalDateTime updateTime = dic.getUpdateTime();
                    return ResultVO.ok(DicDataDetailRes.newBuilder()
                            .setDicKey(dic.getDicKey())
                            .setDicValue(dic.getDicValue())
                            .setDicDesc(dic.getDicDesc())
                            .setSort(dic.getSort())
                            .setParentId(dic.getParentId())
                            .setCreateTime(Optional.ofNullable(createTime)
                                    .map(dateTimeFormatter::format)
                                    .orElse(""))
                            .setUpdateTime(Optional.ofNullable(updateTime)
                                    .map(dateTimeFormatter::format)
                                    .orElse(""))
                            .addAllChildren(list)
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("查询字典数据失败", e);
                    return Mono.just(ResultVO.fail("查询失败"));
                })
                .onErrorReturn(ResultVO.fail("查询字典数据失败"));
    }
}