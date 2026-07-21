-- AI 中心菜单（youlai_system）
USE `youlai_system`;

DELETE FROM sys_role_menu WHERE menu_id >= 200 AND menu_id < 220;
DELETE FROM sys_menu WHERE id >= 200 AND id < 220;

INSERT INTO sys_menu (id, parent_id, type, name, path, component, perm, icon, sort, visible, redirect, tree_path, always_show, keep_alive, create_time, update_time) VALUES
(200, 0, 2, 'AI中心', '/ai', 'Layout', NULL, 'el-icon-Cpu', 6, 1, '/ai/settings', '0', 1, 1, NOW(), NOW()),
(201, 200, 1, '模型配置', 'settings', 'ai/settings/index', 'ai:settings:view', 'el-icon-Setting', 1, 1, NULL, '0,200', 0, 1, NOW(), NOW()),
(202, 200, 1, '运营助手', 'assistant', 'ai/assistant/index', 'ai:assistant:view', 'el-icon-ChatDotRound', 2, 1, NULL, '0,200', 0, 1, NOW(), NOW()),
(203, 200, 1, '知识库', 'knowledge', 'ai/knowledge/index', 'ai:knowledge:view', 'el-icon-Collection', 3, 1, NULL, '0,200', 0, 1, NOW(), NOW()),
(204, 200, 1, '工单Agent', 'ticket', 'ai/ticket/index', 'ai:ticket:view', 'el-icon-Tickets', 4, 1, NULL, '0,200', 0, 1, NOW(), NOW()),
(205, 200, 1, '数据洞察', 'insight', 'ai/insight/index', 'ai:insight:view', 'el-icon-DataAnalysis', 5, 1, NULL, '0,200', 0, 1, NOW(), NOW()),
(210, 201, 4, '保存模型配置', NULL, NULL, 'ai:settings:edit', NULL, 1, 1, NULL, '0,200,201', 0, 0, NOW(), NOW());

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2,200),(2,201),(2,202),(2,203),(2,204),(2,205),(2,210);
