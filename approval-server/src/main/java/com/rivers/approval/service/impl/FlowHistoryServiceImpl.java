package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.entity.FlowTaskDone;
import com.rivers.approval.repository.FlowHistoryRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
import com.rivers.approval.repository.FlowTaskDoneRepository;
import com.rivers.approval.service.IFlowHistoryService;
import com.rivers.core.vo.ResultVO;

import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 流程历史服务实现。
 * <p>
 * 历史记录为结构化列存储，proto 已扩展对应字段，
 * 前端可直接读取节点/任务/办理人/结果/意见/状态变更等完整轨迹。
 * 另提供跟踪链视图（trackByInstance）：把任务事件按节点实例聚合为
 * "发起 → 各审批环节（含转交行为）"，每行仅保留展示所需字段。
 */
@Service
@Slf4j
public class FlowHistoryServiceImpl implements IFlowHistoryService {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    private static final String INSTANCE_STARTED = "INSTANCE_STARTED";
    private static final String TASK_CREATED = "TASK_CREATED";
    private static final String TASK_COMPLETED = "TASK_COMPLETED";
    private static final String TRANSFERRED = "TRANSFERRED";
    private static final String CLAIMED = "CLAIMED";

    private final FlowHistoryRepository historyRepo;
    private final FlowInstanceRepository instanceRepo;
    private final FlowTaskDoneRepository taskDoneRepo;

    public FlowHistoryServiceImpl(FlowHistoryRepository historyRepo,
                                  FlowInstanceRepository instanceRepo,
                                  FlowTaskDoneRepository taskDoneRepo) {
        this.historyRepo = historyRepo;
        this.instanceRepo = instanceRepo;
        this.taskDoneRepo = taskDoneRepo;
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
        var historiesMono = historyRepo.findByInstanceId(req.getInstanceId()).collectList();
        var doneMono = taskDoneRepo.findByInstanceId(req.getInstanceId()).collectList();
        return Mono.zip(historiesMono, doneMono)
                .flatMap(tuple -> instanceRepo.findById(req.getInstanceId())
                        .map(instance -> buildTrack(instance, tuple.getT1(), tuple.getT2()))
                        .defaultIfEmpty(buildTrack(null, tuple.getT1(), tuple.getT2())))
                .map(tracks -> ResultVO.ok(
                        FlowTrackRes.newBuilder().addAllTracks(tracks).build()));
    }

    // ==================== 跟踪链聚合 ====================

    /**
     * 任务事件按节点实例聚合 + 转交记录（flow_task_done）注入环节行。
     */
    private List<FlowTrackItem> buildTrack(FlowInstance instance,
                                           List<FlowHistory> histories,
                                           List<FlowTaskDone> doneList) {
        var tracks = new ArrayList<FlowTrackItem>();
        var started = findFirst(histories, INSTANCE_STARTED);
        var nodeGroups = groupTaskEventsByNode(histories);
        var transfersByNode = doneList.stream()
                .filter(d -> TRANSFERRED.equals(d.getStatus()) && d.getNodeInstanceId() != null)
                .collect(Collectors.groupingBy(FlowTaskDone::getNodeInstanceId,
                        LinkedHashMap::new, Collectors.toList()));
        // 发起行
        tracks.add(FlowTrackItem.newBuilder()
                .setNodeName("发起申请")
                .setAssignee(initiatorText(instance))
                .setTaskTime(fmt(started != null ? started.getCreateTime() : null))
                .setNextAssignee(nodeGroups.isEmpty()
                        ? "" : resolvedAssignee(nodeGroups.get(0)))
                .setStatus("STARTED")
                .build());
        // 审批环节行（下一处理人 = 下一环节的办理人）
        for (int i = 0; i < nodeGroups.size(); i++) {
            var events = nodeGroups.get(i);
            var next = i + 1 < nodeGroups.size()
                    ? resolvedAssignee(nodeGroups.get(i + 1)) : "";
            var transfers = transfersByNode.getOrDefault(
                    events.getFirst().getNodeInstanceId(), List.of());
            tracks.addAll(toNodeTracks(events, transfers, next, doneList));
        }
        return tracks;
    }

    /**
     * 任务事件按节点实例聚合，保持时间序。
     * 注意：flow_history 表 node_instance_id/task_id 为 NOT NULL DEFAULT 0，
     * 节点级/实例级事件读回后 id=0，必须用事件类型白名单过滤，
     * 否则非任务事件会被误当任务组聚合出空环节行。
     */
    private List<List<FlowHistory>> groupTaskEventsByNode(List<FlowHistory> histories) {
        var groups = new LinkedHashMap<Long, List<FlowHistory>>();
        histories.stream()
                .filter(this::isTaskEvent)
                .forEach(h -> groups.computeIfAbsent(
                        h.getNodeInstanceId(), k -> new ArrayList<>()).add(h));
        return new ArrayList<>(groups.values());
    }

    private boolean isTaskEvent(FlowHistory h) {
        return h.getNodeInstanceId() != null
                && (TASK_CREATED.equals(h.getEventType())
                || TASK_COMPLETED.equals(h.getEventType()));
    }

    /**
     * 单环节的跟踪行：人工认领行（若有）→ 转交行（若有）→ 审批行，按审批时间正序。
     * <p>
     * 认领判定：flow_task_done.claimed_time 非空即发生过人工认领；
     * 转交产生的新任务不写 claimed_time，自动接单不会误判为领单。
     */
    private List<FlowTrackItem> toNodeTracks(List<FlowHistory> events,
                                             List<FlowTaskDone> transfers,
                                             String nextAssignee,
                                             List<FlowTaskDone> doneList) {
        var created = findFirst(events, TASK_CREATED);
        var completed = findFirst(events, TASK_COMPLETED);
        var first = events.getFirst();
        var nodeInstanceId = first.getNodeInstanceId();
        var nodeName = str(first.getTaskName());
        var taskTime = fmt(created != null ? created.getCreateTime() : first.getCreateTime());
        // 人工认领行
        var claimTracks = doneList.stream()
                .filter(d -> d.getClaimedTime() != null
                        && nodeInstanceId.equals(d.getNodeInstanceId()))
                .map(d -> FlowTrackItem.newBuilder()
                        .setNodeName(str(d.getTaskName()).isBlank() ? nodeName : str(d.getTaskName()))
                        .setAssignee(str(d.getAssignee()))
                        .setTaskTime(fmt(d.getCreateTime()))
                        .setApproveTime(fmt(d.getClaimedTime()))
                        .setNextAssignee(str(d.getAssignee()))
                        .setStatus(CLAIMED)
                        .build())
                .toList();
        // 转交行
        var transferTracks = transfers.stream()
                .map(t -> FlowTrackItem.newBuilder()
                        .setNodeName(nodeName)
                        .setAssignee(str(t.getAssignee()))
                        .setTaskTime(taskTime)
                        .setApproveTime(fmt(t.getEndTime()))
                        .setNextAssignee(newAssigneeOf(t, doneList))
                        .setStatus(TRANSFERRED)
                        .setOpinion(str(t.getComment()))
                        .build())
                .toList();
        // 审批行
        var approveTrack = FlowTrackItem.newBuilder()
                .setNodeName(nodeName)
                .setAssignee(resolvedAssignee(events))
                .setTaskTime(taskTime)
                .setApproveTime(fmt(completed != null ? completed.getCreateTime() : null))
                .setNextAssignee(nextAssignee)
                .setStatus(completed != null ? str(completed.getResult()) : "PENDING")
                .setOpinion(completed != null ? str(completed.getOpinion()) : "")
                .build();
        // 合并并按审批时间正序（待审批行时间空，恒排最后）
        return Stream.of(claimTracks, transferTracks, List.of(approveTrack))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(
                        (FlowTrackItem t) -> t.getApproveTime().isEmpty() ? null : t.getApproveTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 环节办理人：已审批取实际办理人；未审批拼接全部候选（去重）。
     */
    private String resolvedAssignee(List<FlowHistory> events) {
        var completed = findFirst(events, TASK_COMPLETED);
        if (completed != null && !str(completed.getAssignee()).isBlank()) {
            return str(completed.getAssignee());
        }
        return events.stream()
                .filter(h -> TASK_CREATED.equals(h.getEventType()))
                .map(FlowHistory::getAssignee)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * 转交目标人：已办表中 prev_task_id 指向该转交任务的后继归档行办理人。
     */
    private String newAssigneeOf(FlowTaskDone transfer, List<FlowTaskDone> doneList) {
        return doneList.stream()
                .filter(d -> transfer.getId() != null
                        && transfer.getId().equals(d.getPrevTaskId()))
                .map(FlowTaskDone::getAssignee)
                .filter(a -> a != null && !a.isBlank())
                .findFirst()
                .orElse("");
    }

    private FlowHistory findFirst(List<FlowHistory> histories, String eventType) {
        return histories.stream()
                .filter(h -> eventType.equals(h.getEventType()))
                .findFirst()
                .orElse(null);
    }

    private String initiatorText(FlowInstance instance) {
        if (instance == null) {
            return "";
        }
        var name = str(instance.getInitiatorName());
        var id = str(instance.getInitiator());
        return name.isBlank() ? id : name + "(" + id + ")";
    }

    // ==================== 实体转换 ====================

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

    private String fmt(LocalDateTime time) {
        return Optional.ofNullable(time)
                .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                .orElse("");
    }

    private static String str(String v) {
        return v != null ? v : "";
    }
}