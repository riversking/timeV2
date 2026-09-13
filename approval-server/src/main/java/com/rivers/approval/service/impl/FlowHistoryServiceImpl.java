package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.entity.FlowTrack;
import com.rivers.approval.repository.FlowHistoryRepository;
import com.rivers.approval.repository.FlowTrackRepository;
import com.rivers.approval.service.IFlowHistoryService;
import com.rivers.core.vo.ResultVO;

import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 流程历史服务实现。
 * <p>
 * 审计流水见 flow_history（listByInstance 等）；流程跟踪链直接读
 * flow_track 展示表 —— 每个行为发生时已按行入库，查询零聚合。
 */
@Service
@Slf4j
public class FlowHistoryServiceImpl implements IFlowHistoryService {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowHistoryRepository historyRepo;
    private final FlowTrackRepository trackRepo;

    public FlowHistoryServiceImpl(FlowHistoryRepository historyRepo,
                                  FlowTrackRepository trackRepo) {
        this.historyRepo = historyRepo;
        this.trackRepo = trackRepo;
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

    @Override
    public Mono<ResultVO<FlowTrackRes>> trackByInstance(InstanceIdReq req) {
        return trackRepo.findByInstanceId(req.getInstanceId())
                .map(this::toTrackItem)
                .collectList()
                .map(list -> ResultVO.ok(
                        FlowTrackRes.newBuilder().addAllTracks(list).build()));
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
                .setCreateTime(fmt(i.getCreateTime()))
                .build();
    }

    /**
     * 跟踪行直出：字段一一对应，无任何拼接。
     */
    private FlowTrackItem toTrackItem(FlowTrack t) {
        return FlowTrackItem.newBuilder()
                .setNodeName(str(t.getNodeName()))
                .setAssignee(str(t.getAssignee()))
                .setTaskTime(fmt(t.getTaskTime()))
                .setApproveTime(fmt(t.getActionTime()))
                .setNextAssignee(str(t.getNextAssignee()))
                .setStatus(str(t.getTrackType()))
                .setOpinion(str(t.getOpinion()))
                .build();
    }

    private String fmt(LocalDateTime time) {
        return Optional.ofNullable(time)
                .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                .orElse("");
    }

    private static String str(String v) {
        return v != null ? v : "";
    }
}