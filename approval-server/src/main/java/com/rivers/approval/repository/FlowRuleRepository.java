package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowRule;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowRuleRepository extends ReactiveCrudRepository<FlowRule, Long> {

    /**
     * 根据规则编码查询启用的规则
     */
    @Query("""
        SELECT * FROM flow_rule
        WHERE rule_code = :ruleCode
          AND enabled = 1
          AND is_deleted = 0
        """)
    Mono<FlowRule> findEnabledByCode(String ruleCode);

    /**
     * 查询某定义下某节点的规则链（按优先级倒序，用于排他网关条件路由）
     */
    @Query("""
        SELECT * FROM flow_rule
        WHERE definition_id = :definitionId
          AND node_id = :nodeId
          AND enabled = 1
          AND is_deleted = 0
        ORDER BY priority DESC
        """)
    Flux<FlowRule> findByDefAndNode(Long definitionId, String nodeId);

    /**
     * 查询某定义下所有启用的规则
     */
    @Query("""
        SELECT * FROM flow_rule
        WHERE definition_id = :definitionId
          AND enabled = 1
          AND is_deleted = 0
        ORDER BY priority DESC
        """)
    Flux<FlowRule> findEnabledByDefinitionId(Long definitionId);

    /**
     * 按规则类型查询全局规则（definition_id IS NULL）
     */
    @Query("""
        SELECT * FROM flow_rule
        WHERE rule_type = :ruleType
          AND definition_id IS NULL
          AND enabled = 1
          AND is_deleted = 0
        ORDER BY priority DESC
        """)
    Flux<FlowRule> findGlobalByType(String ruleType);

    /**
     * 更新启用状态
     */
    @Query("""
        UPDATE flow_rule
        SET enabled = :enabled,
            update_user = :operator,
            update_time = NOW()
        WHERE id = :id AND is_deleted = 0
        """)
    Mono<Integer> toggleEnabled(
            @Param("id") Long id,
            @Param("enabled") Integer enabled,
            @Param("operator") String operator);
}
