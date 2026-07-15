package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowHistory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface FlowHistoryRepository extends ReactiveCrudRepository<FlowHistory, Long> {

    /**
     * 查询某实例的完整历史链路（按时间正序）
     */
    @Query("""
        SELECT * FROM flow_history
        WHERE instance_id = :instanceId
          AND is_deleted = 0
        ORDER BY create_time ASC
        """)
    Flux<FlowHistory> findByInstanceId(Long instanceId);

    /**
     * 查询某实例指定事件类型的记录
     */
    @Query("""
        SELECT * FROM flow_history
        WHERE instance_id = :instanceId
          AND event_type = :eventType
          AND is_deleted = 0
        ORDER BY create_time ASC
        """)
    Flux<FlowHistory> findByInstanceIdAndEventType(Long instanceId, String eventType);

    /**
     * 查询某任务的所有历史（审批流转记录）
     */
    @Query("""
        SELECT * FROM flow_history
        WHERE task_id = :taskId
          AND is_deleted = 0
        ORDER BY create_time ASC
        """)
    Flux<FlowHistory> findByTaskId(Long taskId);

    /**
     * 按操作人分页查询操作记录
     */
    @Query("""
        SELECT * FROM flow_history
        WHERE operator_id = :operatorId
          AND is_deleted = 0
        ORDER BY create_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowHistory> findByOperatorWithPage(String operatorId, int offset, int size);
}
