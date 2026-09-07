package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.repository.FlowHistoryRepository;
import com.rivers.approval.service.IFlowHistoryService;
import com.rivers.core.vo.ResultVO;

import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 流程历史服务实现。
 * <p>
 * 历史记录为结构化列存储，proto 已扩展对应字段，
 * 前端可直接读取节点/任务/办理人/结果/意见/状态变更等完整轨迹。
 */
@Service
@Slf4j
public class FlowHistoryServiceImpl implements IFlowHistoryService {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowHistoryRepository historyRepo;

    public FlowHistoryServiceImpl(FlowHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByInstance(InstanceIdReq req) {
        return historyRepo.findByInstanceId(req.getInstanceId())
                .map(this::toHistoryRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByTask(TaskIdReq req) {
        return historyRepo.findByTaskId(req.getTaskId())
                .map(this::toHistoryRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByOperator(OperatorHistoryReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        return historyRepo.findByOperatorWithPage(
                        req.getOperatorId(), offset, req.getPageSize())
                .map(this::toHistoryRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }

    /**
     * Entity → FlowHistoryRes：完整结构化映射（proto 已扩展字段）
     */
    private FlowHistoryRes toHistoryRes(FlowHistory i) {
        return FlowHistoryRes.newBuilder()
                .setId(i.getId())
                .setInstanceId(i.getInstanceId())
                .setInstanceNo(str(i.getInstanceNo()))
                .setNodeInstanceId(i.getNodeInstanceId())
                .setNodeId(str(i.getNodeId()))
                .setNodeName(str(i.getNodeName()))
                .setNodeType(str(i.getNodeType()))
                .setTaskId(i.getTaskId())
                .setTaskNo(str(i.getTaskNo()))
                .setTaskName(str(i.getTaskName()))
                .setAssignee(str(i.getAssignee()))
                .setTargetAssignee(str(i.getTargetAssignee()))
                .setResult(str(i.getResult()))
                .setOpinion(str(i.getOpinion()))
                .setFromStatus(str(i.getFromStatus()))
                .setToStatus(str(i.getToStatus()))
                .setEventType(str(i.getEventType()))
                .setOperatorId(str(i.getOperatorId()))
                .setOperatorName(str(i.getOperatorName()))
                .setRemark(str(i.getRemark()))
                .setCreateTime(Optional.ofNullable(i.getCreateTime())
                        .map(c -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS)
                                .format(c))
                        .orElse(""))
                .build();
    }

    private static String str(String v) {
        return v != null ? v : "";
    }
}