package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;

/**
 * @author xx
 */
@Service
@Slf4j
public class DicServiceImpl implements IDicService {

    public static final String CHECK_DIC_FAIL = "查询字典数据失败";
    public static final String DIC_KEY_EMPTY = "字典key不能为空";
    private static final String DIC_TREE_CACHE_KEY = "all";

    private final TimerDicMapper timerDicMapper;

    /**
     * 字典树扁平列表缓存：字典数据读多写少，全表查询 3.8s 只发生在首次；
     * 写操作（保存/更新/删除）后整体失效，保证数据一致性。
     */
    private final Cache<String, List<DicTreeVO>> dicTreeCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public DicServiceImpl(TimerDicMapper timerDicMapper) {
        this.timerDicMapper = timerDicMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveDic(SaveDicReq saveDicReq) {
        // 参数校验（非阻塞，可提前返回）
        if (StringUtils.isBlank(saveDicReq.getDicKey())) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
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
                    dicTreeCache.invalidateAll();
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
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
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
                    dicTreeCache.invalidateAll();
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
                    // Caffeine get 原子加载：并发请求只有一个触发 DB 查询，防止缓存击穿
                    List<DicTreeVO> flatList = dicTreeCache.get(
                            DIC_TREE_CACHE_KEY, k -> loadDicFlatList());
                    TreeFactory<Long, DicTreeVO> treeFactory = new TreeFactory<>();
                    SequencedCollection<DicTreeVO> tree = treeFactory.buildTreeOrdered(flatList);
                    return ResultVO.ok(tree);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error("构建字典树失败", e);
                    return Mono.just(ResultVO.fail("加载字典失败"));
                });
    }

    /**
     * 查询字典全量数据并映射为扁平 VO（仅缓存穿透时执行）。
     */
    private List<DicTreeVO> loadDicFlatList() {
        long startTime = System.currentTimeMillis();
        LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
        dicWrapper.orderByAsc(TimerDic::getSort)
                .select(TimerDic::getId, TimerDic::getDicKey, TimerDic::getParentId,
                        TimerDic::getDicValue, TimerDic::getSort);
        List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
        log.info("查询字典树耗时: {} ms", System.currentTimeMillis() - startTime);
        return timerDictionaries.stream()
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
    }

    @Override
    public Mono<ResultVO<DicDataRes>> getDicData(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        // ⚠️ 原逻辑有 bug：应该是 isBlank 才报错！
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey)
                            .orderByAsc(TimerDic::getSort)
                            .select(TimerDic::getId);
                    TimerDic timerDic = timerDicMapper.selectOne(dicWrapper);
                    if (timerDic == null) {
                        throw new BusinessException("字典不存在");
                    }
                    dicWrapper.clear();
                    dicWrapper.eq(TimerDic::getParentId, timerDic.getId())
                            .orderByAsc(TimerDic::getSort)
                            .select(TimerDic::getId, TimerDic::getParentId,
                                    TimerDic::getDicKey, TimerDic::getDicValue, TimerDic::getDicDesc, TimerDic::getSort);
                    List<TimerDic> timerDictionaries = timerDicMapper.selectList(dicWrapper);
                    List<Dic> dicList = timerDictionaries.stream()
                            .map(i -> Dic.newBuilder()
                                    .setId(i.getId())
                                    .setDicKey(i.getDicKey())
                                    .setDicValue(i.getDicValue())
                                    .setParentId(i.getParentId())
                                    .setDicDesc(i.getDicDesc())
                                    .setSort(i.getSort())
                                    .build())
                            .toList();
                    return ResultVO.ok(DicDataRes.newBuilder().addAllDicData(dicList).build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error(CHECK_DIC_FAIL, e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    @Override
    public Mono<ResultVO<DicDataDetailRes>> getDicDataDetail(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
                    dicWrapper.eq(TimerDic::getDicKey, dicKey);
                    TimerDic dic = timerDicMapper.selectOne(dicWrapper);
                    if (dic == null) {
                        throw new BusinessException("字典不存在");
                    }
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime createTime = dic.getCreateTime();
                    LocalDateTime updateTime = dic.getUpdateTime();
                    return ResultVO.ok(DicDataDetailRes.newBuilder()
                            .setId(dic.getId())
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
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(Exception.class, e -> {
                    log.error(CHECK_DIC_FAIL, e);
                    return Mono.just(ResultVO.fail("查询失败"));
                })
                .onErrorReturn(ResultVO.fail(CHECK_DIC_FAIL));
    }

    @Override
    public Mono<ResultVO<Void>> deleteDic(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<TimerDic> dicWrapper = Wrappers.lambdaQuery();
            dicWrapper.eq(TimerDic::getDicKey, dicKey);
            TimerDic timerDic = timerDicMapper.selectOne(dicWrapper);
            if (timerDic == null) {
                throw new BusinessException("字典不存在");
            }
            timerDic.deleteById();
            dicTreeCache.invalidateAll();
            return ResultVO.<Void>ok();
        }).onErrorResume(Exception.class, e -> {
            log.error("删除字典失败", e);
            return Mono.just(ResultVO.fail("删除失败"));
        }).onErrorReturn(ResultVO.fail("删除字典失败"));
    }
}