package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowNodeInstance;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowNodeInstanceRepository extends ReactiveCrudRepository<FlowNodeInstance, Long> {

    /**
     * 查询某实例下所有节点实例（按开始时间排序）
     */
    @Query("""
            SELECT * FROM flow_node_instance
            WHERE instance_id = :instanceId
              AND is_deleted = 0
            ORDER BY start_time ASC
            """)
    Flux<FlowNodeInstance> findByInstanceId(Long instanceId);

    /**
     * 查询某实例下指定状态的节点实例
     */
    @Query("""
            SELECT * FROM flow_node_instance
            WHERE instance_id = :instanceId
              AND status = :status
              AND is_deleted = 0
            """)
    Flux<FlowNodeInstance> findByInstanceIdAndStatus(Long instanceId, String status);

    /**
     * 查询某实例下指定节点ID的节点实例（同节点可能因退回产生多条，取最新）
     */
    @Query("""
            SELECT * FROM flow_node_instance
            WHERE instance_id = :instanceId
              AND node_id = :nodeId
              AND is_deleted = 0
            ORDER BY create_time DESC
            LIMIT 1
            """)
    Mono<FlowNodeInstance> findLatestByInstanceIdAndNodeId(Long instanceId, String nodeId);

    /**
     * 查询某实例下当前活跃的节点实例（状态为 ACTIVE 或 PENDING）
     */
    @Query("""
            SELECT * FROM flow_node_instance
            WHERE instance_id = :instanceId
              AND status IN ('ACTIVE', 'PENDING')
              AND is_deleted = 0
            """)
    Flux<FlowNodeInstance> findActiveByInstanceId(Long instanceId);

    /**
     * 查询并行网关 Fork 产生的子分支节点
     */
    @Query("""
            SELECT * FROM flow_node_instance
            WHERE parent_node_instance_id = :parentNodeInstanceId
              AND is_deleted = 0
            """)
    Flux<FlowNodeInstance> findByParentNodeInstanceId(Long parentNodeInstanceId);


    @Query("""
            SELECT * FROM flow_node_instance
            WHERE instance_id = :instanceId
              AND node_id = :nodeId
              AND status = 'ACTIVE'
              AND is_deleted = 0
            ORDER BY create_time ASC
            LIMIT 1
            """)
    Mono<FlowNodeInstance> findActiveByInstanceIdAndNodeId(Long instanceId, String nodeId);

    /**
     * 更新节点状态
     * AND status = 'ACTIVE' 作为乐观锁：并发分支完成同一 Join 节点时，
     * 只有第一个成功者拿到 rows=1 并发布推进事件
     */
    @Modifying
    @Query("""
            UPDATE flow_node_instance
            SET status = :status,
                output_variables = :outputVariables,
                end_time = :endTime,
                update_user = :operator
            WHERE id = :id
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """)
    Mono<Integer> updateNodeStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("outputVariables") String outputVariables,
            @Param("endTime") java.time.LocalDateTime endTime,
            @Param("operator") String operator);

    /**
     * 递增 Join 计数（原子操作，用于并行网关 Join 节点）
     */
    @Modifying
    @Query("""
            UPDATE flow_node_instance
            SET join_count = join_count + 1
            WHERE id = :id
              AND is_deleted = 0
            """)
    Mono<Integer> incrementJoinCount(@Param("id") Long id);

    /**
     * 查询 Join 节点当前计数信息（判断是否所有分支已到达）
     */
    @Query("""
            SELECT join_count, fork_count FROM flow_node_instance
            WHERE id = :id AND is_deleted = 0
            """)
    Mono<FlowNodeInstance> findJoinCountById(@Param("id") Long id);


}