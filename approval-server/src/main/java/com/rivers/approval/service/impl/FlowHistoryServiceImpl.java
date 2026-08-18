package com.rivers.approval.service.impl;

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
 */
@Service
@Slf4j
public class FlowHistoryServiceImpl implements IFlowHistoryService {

    private final FlowHistoryRepository historyRepo;

    public FlowHistoryServiceImpl(FlowHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByInstance(InstanceIdReq req) {
        return historyRepo.findByInstanceId(req.getInstanceId())
                .map(i -> FlowHistoryRes.newBuilder()
                        .setId(i.getId())
                        .setInstanceId(i.getInstanceId())
                        .setNodeInstanceId(i.getNodeInstanceId())
                        .setTaskId(i.getTaskId())
                        .setEventType(i.getEventType())
                        .setOperatorId(i.getOperatorId())
                        .setOperatorName(i.getOperatorName())
                        .setDetail(i.getDetail())
                        .setRemark(i.getRemark())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .format(c))
                                .orElse(""))
                        .build())
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByTask(TaskIdReq req) {
        return historyRepo.findByTaskId(req.getTaskId())
                .map(i -> FlowHistoryRes.newBuilder()
                        .setId(i.getId())
                        .setInstanceId(i.getInstanceId())
                        .setNodeInstanceId(i.getNodeInstanceId())
                        .setTaskId(i.getTaskId())
                        .setEventType(i.getEventType())
                        .setOperatorId(i.getOperatorId())
                        .setOperatorName(i.getOperatorName())
                        .setDetail(i.getDetail())
                        .setRemark(i.getRemark())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .format(c))
                                .orElse(""))
                        .build())
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }

    @Override
    public Mono<ResultVO<HistoryListRes>> listByOperator(OperatorHistoryReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        return historyRepo.findByOperatorWithPage(
                        req.getOperatorId(), offset, req.getPageSize())
                .map(i -> FlowHistoryRes.newBuilder()
                        .setId(i.getId())
                        .setInstanceId(i.getInstanceId())
                        .setNodeInstanceId(i.getNodeInstanceId())
                        .setTaskId(i.getTaskId())
                        .setEventType(i.getEventType())
                        .setOperatorId(i.getOperatorId())
                        .setOperatorName(i.getOperatorName())
                        .setDetail(i.getDetail())
                        .setRemark(i.getRemark())
                        .setCreateTime(Optional.ofNullable(i.getCreateTime())
                                .map(c -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .format(c))
                                .orElse(""))
                        .build())
                .collectList()
                .map(list -> ResultVO.ok(
                        HistoryListRes.newBuilder().addAllHistories(list).build()));
    }
}