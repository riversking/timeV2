CREATE DATABASE IF NOT EXISTS `timer_moment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

USE `timer_moment`;

CREATE TABLE IF NOT EXISTS `moment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content` varchar(1000) NOT NULL DEFAULT '' COMMENT '动态正文',
  `user_id` varchar(50) NOT NULL DEFAULT '' COMMENT '发布人ID',
  `user_name` varchar(100) NOT NULL DEFAULT '' COMMENT '发布人名称(冗余)',
  `create_user` varchar(50) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(50) NOT NULL DEFAULT '' COMMENT '修改人',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除0.否1.是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户动态表';

CREATE TABLE IF NOT EXISTS `moment_image` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `moment_id` bigint unsigned NOT NULL COMMENT '所属动态ID',
  `url` varchar(1024) NOT NULL DEFAULT '' COMMENT '图片URL',
  `sort` int NOT NULL DEFAULT '0' COMMENT '展示顺序(从0开始)',
  `create_user` varchar(50) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` varchar(50) NOT NULL DEFAULT '' COMMENT '修改人',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除0.否1.是',
  PRIMARY KEY (`id`),
  KEY `idx_moment_id` (`moment_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='动态图片表';
