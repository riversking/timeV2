package com.rivers.user.service.impl;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerDic;
import com.rivers.user.repository.TimerDicRepository;
import com.rivers.user.service.IDicService;
import com.rivers.user.vo.DicTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.CompletableFuture;
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
    private static final Integer NOT_DELETED = 0;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TimerDicRepository timerDicRepository;

    /**
     * 字典树扁平列表缓存：字典数据读多写少，全表查询 3.8s 只发生在首次；
     * 写操作（保存/更新/删除）后整体失效，保证数据一致性。
     * 使用 AsyncCache 让缓存加载在 Caffeine 线程池完成，避免阻塞事件循环。
     */
    private final AsyncCache<String, List<DicTreeVO>> dicTreeCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .buildAsync();

    public DicServiceImpl(TimerDicRepository timerDicRepository) {
        this.timerDicRepository = timerDicRepository;
    }

    @Override
    public Mono<ResultVO<Void>> saveDic(SaveDicReq saveDicReq) {
        if (StringUtils.isBlank(saveDicReq.getDicKey())) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        if (StringUtils.isBlank(saveDicReq.getDicValue())) {
            return Mono.just(ResultVO.fail("字典值不能为空"));
        }
        String dicKey = saveDicReq.getDicKey();
        String currentUserId = saveDicReq.getLoginUser().getUserId();
        return timerDicRepository.existsByDicKeyAndIsDeleted(dicKey, NOT_DELETED)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.just(ResultVO.<Void>fail("字典已存在"));
                    }
                    TimerDic timerDic = new TimerDic();
                    timerDic.setDicKey(dicKey);
                    timerDic.setDicValue(saveDicReq.getDicValue());
                    timerDic.setDicDesc(saveDicReq.getDicDesc());
                    timerDic.setParentId(saveDicReq.getParentId() == 0 ? -1L : saveDicReq.getParentId());
                    timerDic.setSort(saveDicReq.getSort());
                    timerDic.setCreateUser(currentUserId);
                    timerDic.setUpdateUser(currentUserId);
                    timerDic.setCreateTime(LocalDateTime.now());
                    timerDic.setUpdateTime(LocalDateTime.now());
                    return timerDicRepository.save(timerDic)
                            .doOnNext(saved -> dicTreeCache.synchronous().invalidateAll())
                            .thenReturn(ResultVO.<Void>ok());
                })
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
        long id = updateDicReq.getId();
        String dicKey = updateDicReq.getDicKey();
        String currentUserId = updateDicReq.getLoginUser().getUserId();
        return timerDicRepository.findByDicKeyAndIsDeleted(dicKey, NOT_DELETED)
                .flatMap(existing -> {
                    if (!existing.getId().equals(id)) {
                        return Mono.just(ResultVO.<Void>fail("字典已存在"));
                    }
                    return doUpdateDic(id, updateDicReq, currentUserId);
                })
                .switchIfEmpty(Mono.defer(() -> doUpdateDic(id, updateDicReq, currentUserId)))
                .onErrorResume(Exception.class, e -> {
                    log.error("更新字典失败", e);
                    return Mono.just(ResultVO.fail("系统异常"));
                });
    }

    private Mono<ResultVO<Void>> doUpdateDic(long id, UpdateDicReq updateDicReq, String currentUserId) {
        return timerDicRepository.updateDic(id, updateDicReq.getDicKey(), updateDicReq.getDicValue(),
                        updateDicReq.getDicDesc(), updateDicReq.getParentId(), updateDicReq.getSort(), currentUserId)
                .doOnNext(rows -> dicTreeCache.synchronous().invalidateAll())
                .map(rows -> ResultVO.<Void>ok());
    }

    @Override
    public Mono<ResultVO<SequencedCollection<DicTreeVO>>> getDicTree() {
        Mono<List<DicTreeVO>> flatListMono = Mono.defer(() -> Mono.fromFuture(loadCachedFlatList()));
        return flatListMono.map(flatList -> {
                    TreeFactory<Long, DicTreeVO> treeFactory = new TreeFactory<>();
                    return ResultVO.ok(treeFactory.buildTreeOrdered(flatList));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("构建字典树失败", e);
                    return Mono.just(ResultVO.fail("加载字典失败"));
                });
    }

    /**
     * Caffeine 异步加载：并发请求只有一个触发 DB 查询，防止缓存击穿；IO 不占用事件循环线程。
     */
    private CompletableFuture<List<DicTreeVO>> loadCachedFlatList() {
        return dicTreeCache.get(DIC_TREE_CACHE_KEY, (key, executor) -> loadDicFlatList().toFuture());
    }

    /**
     * 查询字典全量数据并映射为扁平 VO（仅缓存未命中时执行）。
     */
    private Mono<List<DicTreeVO>> loadDicFlatList() {
        long startTime = System.currentTimeMillis();
        return timerDicRepository.findAllByIsDeletedOrderBySortAsc(NOT_DELETED)
                .map(this::toDicTreeVO)
                .collectList()
                .doOnNext(list -> log.info("查询字典树耗时: {} ms",
                        System.currentTimeMillis() - startTime));
    }

    private DicTreeVO toDicTreeVO(TimerDic dic) {
        DicTreeVO vo = new DicTreeVO();
        vo.setId(dic.getId());
        vo.setDicKey(dic.getDicKey());
        vo.setDicValue(dic.getDicValue());
        vo.setSort(dic.getSort());
        vo.setParentId(dic.getParentId());
        return vo;
    }

    @Override
    public Mono<ResultVO<DicDataRes>> getDicData(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return timerDicRepository.findByDicKeyAndIsDeleted(dicKey, NOT_DELETED)
                .flatMap(parent -> timerDicRepository
                        .findByParentIdAndIsDeletedOrderBySortAsc(parent.getId(), NOT_DELETED)
                        .map(this::toProtoDic)
                        .collectList()
                        .map(dicList -> ResultVO.ok(DicDataRes.newBuilder()
                                .addAllDicData(dicList)
                                .build())))
                .switchIfEmpty(Mono.just(ResultVO.<DicDataRes>fail("字典不存在")))
                .onErrorResume(Exception.class, e -> {
                    log.error(CHECK_DIC_FAIL, e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    private Dic toProtoDic(TimerDic dic) {
        return Dic.newBuilder()
                .setId(dic.getId())
                .setDicKey(dic.getDicKey())
                .setDicValue(dic.getDicValue())
                .setParentId(dic.getParentId())
                .setDicDesc(dic.getDicDesc())
                .setSort(dic.getSort())
                .build();
    }

    @Override
    public Mono<ResultVO<DicDataDetailRes>> getDicDataDetail(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return timerDicRepository.findByDicKeyAndIsDeleted(dicKey, NOT_DELETED)
                .map(dic -> ResultVO.ok(DicDataDetailRes.newBuilder()
                        .setId(dic.getId())
                        .setDicKey(dic.getDicKey())
                        .setDicValue(dic.getDicValue())
                        .setDicDesc(dic.getDicDesc())
                        .setSort(dic.getSort())
                        .setParentId(dic.getParentId())
                        .setCreateTime(formatTime(dic.getCreateTime()))
                        .setUpdateTime(formatTime(dic.getUpdateTime()))
                        .build()))
                .switchIfEmpty(Mono.just(ResultVO.<DicDataDetailRes>fail("字典不存在")))
                .onErrorResume(Exception.class, e -> {
                    log.error(CHECK_DIC_FAIL, e);
                    return Mono.just(ResultVO.fail("查询失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteDic(DicDataReq dicDataReq) {
        String dicKey = dicDataReq.getDicKey();
        if (StringUtils.isBlank(dicKey)) {
            return Mono.just(ResultVO.fail(DIC_KEY_EMPTY));
        }
        return timerDicRepository.findByDicKeyAndIsDeleted(dicKey, NOT_DELETED)
                .flatMap(dic -> timerDicRepository.softDeleteById(dic.getId()))
                .doOnNext(rows -> dicTreeCache.synchronous().invalidateAll())
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(Exception.class, e -> {
                    log.error("删除字典失败", e);
                    return Mono.just(ResultVO.fail("删除失败"));
                });
    }

    private static String formatTime(LocalDateTime time) {
        return Optional.ofNullable(time).map(DATE_TIME_FORMATTER::format).orElse("");
    }
}
