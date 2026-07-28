package com.rivers.approval.model;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.repository.*;

import java.util.Map;

/**
 * 节点处理上下文。
 * 每次调用 NodeHandler.handle() 时组装，作为只读参数传入。
 * repositories 按需注入，避免 context 膨胀但又能让 handler 自主操作 DB。
 */
public record NodeContext(
        FlowInstance              instance,
        FlowNodeInstance          currentNode,
        ProcessDefinition         definition,
        Map<String, Object>       variables,
        FlowEventBus              eventBus,
        FlowInstanceRepository    instanceRepo,
        FlowNodeInstanceRepository nodeRepo,
        FlowTaskRepository        taskRepo,
        FlowHistoryRepository     historyRepo,
        FlowRuleRepository        ruleRepo
) {}
