/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 90500
 Source Host           : localhost:3306
 Source Schema         : timer_approval

 Target Server Type    : MySQL
 Target Server Version : 90500
 File Encoding         : 65001

 Date: 31/08/2026 06:21:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for flow_definition
-- ----------------------------
DROP TABLE IF EXISTS `flow_definition`;
CREATE TABLE `flow_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_key` varchar(128) NOT NULL COMMENT '流程定义标识（唯一业务键，如 leave-apply）',
  `name` varchar(255) NOT NULL COMMENT '流程名称（如 请假申请流程）',
  `description` varchar(512) DEFAULT '' COMMENT '流程描述',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号（同 key 下递增，用于升级）',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, PUBLISHED-已发布, DISABLED-已停用',
  `category` varchar(64) DEFAULT '' COMMENT '流程分类（如 OA、业务）',
  `definition_json` json NOT NULL COMMENT '流程定义 DSL（nodes+edges 的 JSON 描述，含节点类型/配置/连线条件）',
  `icon` varchar(255) DEFAULT '' COMMENT '流程图标URL',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_def_key_version` (`definition_key`,`version`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定义表';

-- ----------------------------
-- Table structure for flow_history
-- ----------------------------
DROP TABLE IF EXISTS `flow_history`;
CREATE TABLE `flow_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '关联流程实例ID',
  `node_instance_id` bigint DEFAULT NULL COMMENT '关联节点实例ID（可为空，用于流程级事件）',
  `task_id` bigint DEFAULT NULL COMMENT '关联任务ID（可为空）',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型: INSTANCE_STARTED, INSTANCE_COMPLETED, INSTANCE_TERMINATED, NODE_STARTED, NODE_COMPLETED, TASK_CREATED, TASK_CLAIMED, TASK_COMPLETED, TASK_CANCELLED, GATEWAY_EVALUATED, etc.',
  `operator_id` varchar(64) DEFAULT '' COMMENT '操作人ID',
  `operator_name` varchar(128) DEFAULT '' COMMENT '操作人姓名',
  `detail` json DEFAULT NULL COMMENT '事件详情（JSON，含变更前后的快照）',
  `remark` varchar(1024) DEFAULT '' COMMENT '备注',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_instance_event` (`instance_id`,`event_type`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程历史记录表';

-- ----------------------------
-- Table structure for flow_instance
-- ----------------------------
DROP TABLE IF EXISTS `flow_instance`;
CREATE TABLE `flow_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_no` varchar(64) NOT NULL COMMENT '流程实例编号（业务流水号，展示用）',
  `definition_id` bigint NOT NULL COMMENT '关联流程定义ID (flow_definition.id)',
  `definition_key` varchar(128) NOT NULL COMMENT '冗余流程定义标识（便于查询）',
  `definition_version` int NOT NULL DEFAULT '1' COMMENT '冗余定义版本号',
  `title` varchar(255) DEFAULT '' COMMENT '实例标题（如 张三的请假申请）',
  `initiator` varchar(64) NOT NULL COMMENT '发起人工号/用户名',
  `initiator_name` varchar(128) DEFAULT '' COMMENT '发起人姓名',
  `business_key` varchar(128) DEFAULT '' COMMENT '关联业务主键（如请假单ID，便于与业务系统打通）',
  `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT '实例状态: RUNNING-运行中, COMPLETED-已完成, TERMINATED-已终止',
  `variables` json DEFAULT NULL COMMENT '流程变量（全局上下文，JSON 结构）',
  `current_node_ids` json DEFAULT NULL COMMENT '当前活跃节点ID列表（并发时可能多个）',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号（current_node_ids 并发更新用）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_no` (`instance_no`),
  KEY `idx_def_key` (`definition_key`),
  KEY `idx_initiator` (`initiator`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例表';

-- ----------------------------
-- Table structure for flow_node_instance
-- ----------------------------
DROP TABLE IF EXISTS `flow_node_instance`;
CREATE TABLE `flow_node_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '关联流程实例ID (flow_instance.id)',
  `node_id` varchar(128) NOT NULL COMMENT '节点ID（对应 DSL 中 node.id）',
  `node_name` varchar(255) NOT NULL DEFAULT '' COMMENT '节点名称（如 部门经理审批）',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型: START-开始, END-结束, USER_TASK-用户任务, EXCLUSIVE_GATEWAY-排他网关, PARALLEL_GATEWAY-并行网关',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '节点状态: PENDING-待处理, ACTIVE-执行中, COMPLETED-已完成, SKIPPED-已跳过',
  `assignee` varchar(64) DEFAULT '' COMMENT '指定处理人（USER_TASK节点使用）',
  `candidate_users` json DEFAULT NULL COMMENT '候选人列表（USER_TASK节点使用，JSON数组）',
  `input_variables` json DEFAULT NULL COMMENT '节点输入变量（进入节点时的上下文快照）',
  `output_variables` json DEFAULT NULL COMMENT '节点输出变量（节点完成后的变更）',
  `parent_node_instance_id` bigint DEFAULT NULL COMMENT '父节点实例ID（并行网关 Fork 出来的子分支使用）',
  `fork_count` int DEFAULT '1' COMMENT 'Fork分支总数（并行网关节点记录）',
  `join_count` int DEFAULT '0' COMMENT '已完成 Join 的分支数（Join 节点计数用）',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '节点开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '节点结束时间',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_node_status` (`instance_id`,`status`),
  KEY `idx_assignee_status` (`assignee`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点实例表';

-- ----------------------------
-- Table structure for flow_rule
-- ----------------------------
DROP TABLE IF EXISTS `flow_rule`;
CREATE TABLE `flow_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code` varchar(128) NOT NULL COMMENT '规则编码（唯一业务键）',
  `name` varchar(255) NOT NULL DEFAULT '' COMMENT '规则名称',
  `description` varchar(512) DEFAULT '' COMMENT '规则描述',
  `rule_type` varchar(64) NOT NULL DEFAULT 'CONDITION' COMMENT '规则类型: CONDITION-条件路由, ACTION-动作执行, VALIDATION-校验',
  `definition_id` bigint DEFAULT NULL COMMENT '关联流程定义ID（可为空，支持全局规则）',
  `node_id` varchar(128) DEFAULT '' COMMENT '关联节点ID（网关节点条件规则）',
  `rule_config` json NOT NULL COMMENT '规则配置（SpEL条件链JSON，含condition/action/breakOnMatch/priority/inputMapping/outputMapping）',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（数值越大优先级越高）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用: 0-禁用, 1-启用',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_def_node` (`definition_id`,`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则定义表';

-- ----------------------------
-- Table structure for flow_task
-- ----------------------------
DROP TABLE IF EXISTS `flow_task`;
CREATE TABLE `flow_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '关联流程实例ID (flow_instance.id)',
  `node_instance_id` bigint NOT NULL COMMENT '关联节点实例ID (flow_node_instance.id)',
  `task_no` varchar(64) NOT NULL COMMENT '任务编号（业务流水号）',
  `task_name` varchar(255) NOT NULL DEFAULT '' COMMENT '任务名称（继承节点名称）',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING-待认领, CLAIMED-已认领, COMPLETED-已完成, CANCELLED-已取消, TRANSFERRED-已转交',
  `assignee` varchar(64) DEFAULT '' COMMENT '指定处理人（如为空则需认领）',
  `candidate_users` json DEFAULT NULL COMMENT '候选人列表（JSON数组，认领池依据）',
  `claimed_by` varchar(64) DEFAULT '' COMMENT '认领人',
  `claimed_time` datetime DEFAULT NULL COMMENT '认领时间',
  `completed_by` varchar(64) DEFAULT '' COMMENT '完成人',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `result` varchar(32) DEFAULT '' COMMENT '处理结果: APPROVED-通过, REJECTED-驳回, etc.',
  `comment` varchar(1024) DEFAULT '' COMMENT '审批意见',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级: 0-普通, 1-紧急, 2-非常紧急',
  `prev_task_id` bigint DEFAULT NULL COMMENT '前驱任务ID（转交场景追溯）',
  `create_user` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_assignee_status` (`assignee`,`status`),
  KEY `idx_claimed_by_status` (`claimed_by`,`status`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程任务表（任务池）';

SET FOREIGN_KEY_CHECKS = 1;
