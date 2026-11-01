-- ----------------------------
-- 球员赛季联盟对比表（手动建表；服务启动不再自动执行 DDL）
-- 数据由 syncPlayerSeasonStatsTask 同步后预计算维护
-- ----------------------------
CREATE TABLE IF NOT EXISTS `player_season_league_stat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `season` int NOT NULL DEFAULT 0 COMMENT '赛季（结束年份）',
  `player_id` bigint NOT NULL DEFAULT 0 COMMENT '球员id',
  `stat_key` varchar(32) NOT NULL DEFAULT '' COMMENT '指标标识',
  `stat_label` varchar(32) NOT NULL DEFAULT '' COMMENT '指标名称',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `player_value` double NOT NULL DEFAULT 0 COMMENT '球员场均',
  `league_avg` double NOT NULL DEFAULT 0 COMMENT '联盟平均',
  `league_max` double NOT NULL DEFAULT 0 COMMENT '联盟最高',
  `rank_no` int NOT NULL DEFAULT 0 COMMENT '联盟名次',
  `total_players` int NOT NULL DEFAULT 0 COMMENT '参与排名人数',
  `create_user` varchar(50) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(50) NOT NULL DEFAULT '' COMMENT '修改人',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '是否删除0.否1.是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_player_stat` (`season`, `player_id`, `stat_key`),
  KEY `idx_player` (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='球员赛季联盟对比表';

-- ----------------------------
-- 球员历年数据表（手动建表；季前赛/常规赛/季后赛，存赛季合计）
-- 数据由 syncPlayerSeasonStatsTask 增量回填与当季刷新维护
-- ----------------------------
CREATE TABLE IF NOT EXISTS `player_season_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `season` int NOT NULL DEFAULT 0 COMMENT '赛季（结束年份）',
  `season_type` int NOT NULL DEFAULT 2 COMMENT '赛程分类：1季前赛 2常规赛 3季后赛',
  `player_id` bigint NOT NULL DEFAULT 0 COMMENT '球员id（库内体系）',
  `player_name` varchar(100) NOT NULL DEFAULT '' COMMENT '球员姓名',
  `team` varchar(16) NOT NULL DEFAULT '' COMMENT '球队缩写',
  `games` int NOT NULL DEFAULT 0 COMMENT '出场场次',
  `minutes` double NOT NULL DEFAULT 0 COMMENT '总出场分钟',
  `points` double NOT NULL DEFAULT 0 COMMENT '总得分',
  `field_goals_made` double NOT NULL DEFAULT 0 COMMENT '投篮命中总数',
  `field_goals_attempted` double NOT NULL DEFAULT 0 COMMENT '投篮出手总数',
  `three_pointers_made` double NOT NULL DEFAULT 0 COMMENT '三分命中总数',
  `three_pointers_attempted` double NOT NULL DEFAULT 0 COMMENT '三分出手总数',
  `free_throws_made` double NOT NULL DEFAULT 0 COMMENT '罚球命中总数',
  `free_throws_attempted` double NOT NULL DEFAULT 0 COMMENT '罚球出手总数',
  `rebounds` double NOT NULL DEFAULT 0 COMMENT '总篮板',
  `assists` double NOT NULL DEFAULT 0 COMMENT '总助攻',
  `steals` double NOT NULL DEFAULT 0 COMMENT '总抢断',
  `blocked_shots` double NOT NULL DEFAULT 0 COMMENT '总盖帽',
  `fouls` double NOT NULL DEFAULT 0 COMMENT '总犯规',
  `create_user` varchar(50) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(50) NOT NULL DEFAULT '' COMMENT '修改人',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '是否删除0.否1.是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_type_player` (`season`, `season_type`, `player_id`),
  KEY `idx_player` (`player_id`),
  KEY `idx_season_type` (`season`, `season_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='球员历年数据表（季前赛/常规赛/季后赛）';
