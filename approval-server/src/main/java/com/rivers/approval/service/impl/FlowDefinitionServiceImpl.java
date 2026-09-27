package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowDefinition;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.rule.RuleEngineFacade;
import com.rivers.approval.service.IFlowDefinitionService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 流程定义服务实现。
 * <p>
 * 业务失败不抛异常：统一返回 ResultVO.fail(msg)。
 * create 时校验内嵌规则（排他网关节点 config.rules）：DSL 可解析、condition 非空且 SpEL 语法合法、
 * targetNodeId 属于该网关出边目标集合——校验经 {@link RuleEngineFacade} 执行
 * （local 进程内 / remote 独立规则服务），规则直接配置在流程中，无需入库。
 */
@Service
@Slf4j
public class FlowDefinitionServiceImpl implements IFlowDefinitionService {

    private static final String FLOW_DEF_FAIL = "流程定义不存在: ";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowDefinitionRepository defRepo;
    private final RuleEngineFacade ruleEngineFacade;

    public FlowDefinitionServiceImpl(FlowDefinitionRepository defRepo,
                                     RuleEngineFacade ruleEngineFacade) {
        this.defRepo = defRepo;
        this.ruleEngineFacade = ruleEngineFacade;
    }

    // ==================== 查询 ====================

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> getLatest(GetDefinitionReq req) {
        return defRepo.findLatestPublishedByKey(req.getDefinitionKey())
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
                .map(ResultVO::ok)
                .switchIfEmpty(Mono.just(ResultVO.fail(
                        "流程定义不存在或未发布: " + req.getDefinitionKey())));
    }

    @Override
    public Mono<ResultVO<FlowDefinitionRes>> getByKeyAndVersion(GetDefinitionReq req) {
        return defRepo.findByKeyAndVersion(req.getDefinitionKey(), req.getVersion())
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
                .map(ResultVO::ok)
                .switchIfEmpty(Mono.just(ResultVO.fail(
                        FLOW_DEF_FAIL + req.getDefinitionKey() + " v" + req.getVersion())));
    }

    // ==================== 生命周期 ====================

    @Override
    public Mono<ResultVO<Void>> create(CreateDefinitionReq req) {
        // 定义级校验（内嵌规则直接配置在流程中，创建时校验一次长期有效；local 进程内 / remote 规则服务）
        return ruleEngineFacade.validateDefinition(req.getDefinitionJson())
                .flatMap(error -> error
                        .map(msg -> Mono.just(ResultVO.<Void>fail(msg)))
                        .orElseGet(() -> saveDefinition(req)));
    }

    private Mono<ResultVO<Void>> saveDefinition(CreateDefinitionReq req) {
        var loginUser = req.getLoginUser();
        var def = FlowDefinition.builder()
                .definitionKey(req.getDefinitionKey())
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .icon(req.getIcon())
                .definitionJson(req.getDefinitionJson())
                .version(1)
                .status("DRAFT")
                .createUser(loginUser.getUserId())
                .updateUser(loginUser.getUserId())
                .build();
        return defRepo.save(def)
                .doOnNext(d -> log.info("[FlowDefinitionServiceImpl] 定义已创建 definitionKey={}",
                        d.getDefinitionKey()))
                .map(_ -> ResultVO.<Void>ok());
    }

    @Override
    public Mono<ResultVO<Void>> publish(PublishDefinitionReq req) {
        var loginUser = req.getLoginUser();
        return defRepo.findById(req.getId())
                .flatMap(def -> {
                    if (!"DRAFT".equals(def.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("只能发布草稿状态的流程定义"));
                    }
                    def.setStatus("PUBLISHED");
                    def.setUpdateUser(loginUser.getUserId());
                    return defRepo.save(def)
                            .doOnNext(d -> log.info(
                                    "[FlowDefinitionServiceImpl] 定义已发布 definitionKey={}",
                                    d.getDefinitionKey()))
                            .map(_ -> ResultVO.<Void>ok());
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(FLOW_DEF_FAIL + req.getId())));
    }

    @Override
    public Mono<ResultVO<Void>> disable(DisableDefinitionReq req) {
        var loginUser = req.getLoginUser();
        return defRepo.findById(req.getId())
                .flatMap(def -> {
                    def.setStatus("DISABLED");
                    def.setUpdateUser(loginUser.getUserId());
                    return defRepo.save(def)
                            .doOnNext(d -> log.info(
                                    "[FlowDefinitionServiceImpl] 定义已停用 definitionKey={}",
                                    d.getDefinitionKey()))
                            .map(_ -> ResultVO.<Void>ok());
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(FLOW_DEF_FAIL + req.getId())));
    }
}