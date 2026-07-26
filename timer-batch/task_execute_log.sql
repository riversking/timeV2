-- 定时任务执行日志表
CREATE TABLE IF NOT EXISTS task_execute_log (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_name   VARCHAR(255) DEFAULT ''  COMMENT '任务名称',
    job_name    VARCHAR(255) DEFAULT ''  COMMENT '定时任务名称',
    server_name VARCHAR(255) DEFAULT ''  COMMENT '目标服务名',
    executor_ip VARCHAR(64)  DEFAULT ''  COMMENT '执行器IP',
    target_ip   VARCHAR(512) DEFAULT ''  COMMENT '目标服务IP(多个逗号分隔)',
    execute_time BIGINT      DEFAULT 0   COMMENT '执行耗时(ms)',
    status      VARCHAR(32)  DEFAULT ''  COMMENT '执行状态: SUCCESS / FAILED',
    error_msg   TEXT                     COMMENT '错误信息',
    trigger_type VARCHAR(32) DEFAULT ''  COMMENT '触发类型: quartz / manual',
    create_user VARCHAR(64)  DEFAULT ''  COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user VARCHAR(64)  DEFAULT ''  COMMENT '更新人',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT      DEFAULT 0   COMMENT '逻辑删除: 0=否, 1=是',
    INDEX idx_task_name (task_name),
    INDEX idx_server_name (server_name),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志';
