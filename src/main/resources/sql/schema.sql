-- ============================================
-- 商城项目 RBAC 数据库（简化版）
-- ============================================

CREATE DATABASE IF NOT EXISTS `mall_db`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `mall_db`;

-- 1. 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `username` VARCHAR(50) NOT NULL COMMENT '登录用户名',
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
    `phone` VARCHAR(20) COMMENT '手机号码',
    `status` TINYINT DEFAULT 1 COMMENT '账户状态：1-启用，0-禁用',
    `login_failures` INT DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until` DATETIME COMMENT '账户锁定截止时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 角色表
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色显示名称',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色代码，如 SUPER_ADMIN',
    `description` VARCHAR(200) COMMENT '角色描述',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 3. 权限表
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限主键',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限显示名称',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '权限代码，如 product:view',
    `resource_type` VARCHAR(20) DEFAULT 'api' COMMENT '资源类型',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 4. 用户-角色关联表
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 5. 角色-权限关联表
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 6. 商品分类表
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类主键',
    `category_name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT UNSIGNED DEFAULT 0 COMMENT '父分类ID，0 表示顶级',
    `description` VARCHAR(500) COMMENT '分类描述',
    `icon_url` VARCHAR(255) COMMENT '分类图标 URL',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================
-- 初始化数据
-- ============================================

-- 插入角色
INSERT INTO `role` (`id`, `role_name`, `role_code`, `description`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '所有权限'),
(2, '商家', 'MERCHANT', '商品和订单管理'),
(3, '普通用户', 'USER', '浏览和购买');

-- 插入权限
INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `resource_type`) VALUES
(1, '查看商品', 'product:view', 'api'),
(2, '管理商品', 'product:manage', 'api'),
(3, '查看订单', 'order:view', 'api'),
(4, '管理订单', 'order:manage', 'api'),
(5, '用户管理', 'user:manage', 'api');

-- 分配权限给角色
-- 超级管理员：所有权限
INSERT INTO `role_permission` VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5);

-- 商家：商品和订单管理
INSERT INTO `role_permission` VALUES
(2, 1), (2, 2), (2, 3), (2, 4);

-- 普通用户：只能查看
INSERT INTO `role_permission` VALUES
(3, 1), (3, 3);

-- 插入测试用户（密码: 123456）
INSERT INTO `user` (`id`, `username`, `password`, `phone`, `status`, `created_at`, `updated_at`) VALUES
(1, 'admin', '$2a$10$TDjDSaYbgguPDpqmH7m/5OUFX.dfewgG72SDMxOCPsugkkbngOr92', '13800000001', 1, NOW(), NOW()),
(2, 'merchant', '$2a$10$TDjDSaYbgguPDpqmH7m/5OUFX.dfewgG72SDMxOCPsugkkbngOr92', '13800000002', 1, NOW(), NOW()),
(3, 'user', '$2a$10$TDjDSaYbgguPDpqmH7m/5OUFX.dfewgG72SDMxOCPsugkkbngOr92', '13800000003', 1, NOW(), NOW());

-- 分配角色
INSERT INTO `user_role` VALUES
(1, 1),
(2, 2),
(3, 3);

-- 插入商品分类示例数据
INSERT INTO `category` (`category_name`, `parent_id`, `description`, `icon_url`, `sort_order`, `status`) VALUES
('电子产品', 0, '各类电子产品', '/icons/electronics.png', 1, 1),
('服装', 0, '男女服装', '/icons/clothing.png', 2, 1),
('家居', 0, '家居用品', '/icons/home.png', 3, 1),
('手机', 1, '智能手机', '/icons/phone.png', 1, 1),
('电脑', 1, '笔记本电脑和台式机', '/icons/computer.png', 2, 1),
('男装', 2, '男士服装', '/icons/men.png', 1, 1),
('女装', 2, '女士服装', '/icons/women.png', 2, 1);

SELECT '数据库创建完成！' AS message;
