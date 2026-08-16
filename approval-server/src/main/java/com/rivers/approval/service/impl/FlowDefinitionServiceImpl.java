package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowDefinition;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.service.IFlowDefinitionService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 流程定义服务实现。
 */
@Service
@Slf4j
public class FlowDefinitionServiceImpl implements IFlowDefinitionService {

    private static final String ADMIN = "ADMIN";
    private static final String FLOW_DEF_FAIL = "流程定义不存在: ";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowDefinitionRepository defRepo;

    public FlowDefinitionServiceImpl(FlowDefinitionRepository defRepo) {
        this.defRepo = defRepo;
    }

    // ==================== 查询 ====================

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> getLatest(GetDefinitionReq req) {
        return defRepo.findLatestPublishedByKey(req.getDefinitionKey())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("流程定义不存在或未发布: " + req.getDefinitionKey())))
                .map(i -> FlowDefinitionRes.newBuilder()
                        .setId(i.getId())
                        .setDefinitionKey(i.getDefinitionKey())
                        .setName(i.getName())
                        .setDescription(i.getDescription())
                        .setVersion(i.getVersion())
                        .setStatus(i.getStatus())
                        .setCategory(i.getCategory())
                        .setDefinitionJson(i.getDefinitionJson())
                        .setIcon(i.getIcon())
                        .setCreateUser(i.getCreateUser())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .setUpdateUser(i.getUpdateUser())
                        .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .build())
                .map(ResultVO::ok);
    }

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> getByKeyAndVersion(GetDefinitionReq req) {
        return defRepo.findByKeyAndVersion(req.getDefinitionKey(), req.getVersion())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(FLOW_DEF_FAIL
                                + req.getDefinitionKey() + " v" + req.getVersion())))
                .map(i -> FlowDefinitionRes.newBuilder()
                        .setId(i.getId())
                        .setDefinitionKey(i.getDefinitionKey())
                        .setName(i.getName())
                        .setDescription(i.getDescription())
                        .setVersion(i.getVersion())
                        .setStatus(i.getStatus())
                        .setCategory(i.getCategory())
                        .setDefinitionJson(i.getDefinitionJson())
                        .setIcon(i.getIcon())
                        .setCreateUser(i.getCreateUser())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .setUpdateUser(i.getUpdateUser())
                        .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .build())
                .map(ResultVO::ok);
    }

    // ==================== 生命周期 ====================

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> create(CreateDefinitionReq req) {
        var def = FlowDefinition.builder()
                .definitionKey(req.getDefinitionKey())
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .icon(req.getIcon())
                .definitionJson(req.getDefinitionJson())
                .version(1)
                .status("DRAFT")
                .createUser(ADMIN)
                .updateUser(ADMIN)
                .build();
        return defRepo.save(def)
                .doOnNext(d -> log.info("[FlowDefinitionServiceImpl] 定义已创建 definitionKey={}",
                        d.getDefinitionKey()))
                .map(i -> FlowDefinitionRes.newBuilder()
                        .setId(i.getId())
                        .setDefinitionKey(i.getDefinitionKey())
                        .setName(i.getName())
                        .setDescription(i.getDescription())
                        .setVersion(i.getVersion())
                        .setStatus(i.getStatus())
                        .setCategory(i.getCategory())
                        .setDefinitionJson(i.getDefinitionJson())
                        .setIcon(i.getIcon())
                        .setCreateUser(i.getCreateUser())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .setUpdateUser(i.getUpdateUser())
                        .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .build())
                .map(ResultVO::ok);
    }

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> publish(PublishDefinitionReq req) {
        return defRepo.findById(req.getId())
                .switchIfEmpty(Mono.<FlowDefinition>error(
                        new IllegalArgumentException(FLOW_DEF_FAIL + req.getId())))
                .flatMap(def -> {
                    if (!"DRAFT".equals(def.getStatus())) {
                        return Mono.<FlowDefinition>error(
                                new IllegalStateException("只能发布草稿状态的流程定义"));
                    }
                    def.setStatus("PUBLISHED");
                    def.setUpdateUser(ADMIN);
                    def.setUpdateTime(LocalDateTime.now());
                    return defRepo.save(def);
                })
                .doOnNext(d -> log.info("[FlowDefinitionServiceImpl] 定义已发布 definitionKey={}",
                        d.getDefinitionKey()))
                .map(i -> FlowDefinitionRes.newBuilder()
                        .setId(i.getId())
                        .setDefinitionKey(i.getDefinitionKey())
                        .setName(i.getName())
                        .setDescription(i.getDescription())
                        .setVersion(i.getVersion())
                        .setStatus(i.getStatus())
                        .setCategory(i.getCategory())
                        .setDefinitionJson(i.getDefinitionJson())
                        .setIcon(i.getIcon())
                        .setCreateUser(i.getCreateUser())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .setUpdateUser(i.getUpdateUser())
                        .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .build())
                .map(ResultVO::ok);
    }

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> disable(DisableDefinitionReq req) {
        return defRepo.findById(req.getId())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(FLOW_DEF_FAIL + req.getId())))
                .flatMap(def -> {
                    def.setStatus("DISABLED");
                    def.setUpdateUser(ADMIN);
                    def.setUpdateTime(LocalDateTime.now());
                    return defRepo.save(def);
                })
                .doOnNext(d -> log.info("[FlowDefinitionServiceImpl] 定义已停用 definitionKey={}",
                        d.getDefinitionKey()))
                .map(i -> FlowDefinitionRes.newBuilder()
                        .setId(i.getId())
                        .setDefinitionKey(i.getDefinitionKey())
                        .setName(i.getName())
                        .setDescription(i.getDescription())
                        .setVersion(i.getVersion())
                        .setStatus(i.getStatus())
                        .setCategory(i.getCategory())
                        .setDefinitionJson(i.getDefinitionJson())
                        .setIcon(i.getIcon())
                        .setCreateUser(i.getCreateUser())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .setUpdateUser(i.getUpdateUser())
                        .setUpdateTime(Optional.ofNullable(i.getUpdateTime())
                                .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                        .format(c))
                                .orElse(""))
                        .build())
                .map(ResultVO::ok);
    }
}