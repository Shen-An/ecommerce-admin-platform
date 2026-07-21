-- AI 服务库与核心表（MVP）
CREATE DATABASE IF NOT EXISTS `mall_ai` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mall_ai`;

CREATE TABLE IF NOT EXISTS `ai_session` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT                DEFAULT NULL COMMENT '用户ID',
  `scene`         VARCHAR(32)  NOT NULL COMMENT '场景: assistant/knowledge/ticket/insight',
  `title`         VARCHAR(128)          DEFAULT NULL COMMENT '会话标题',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1进行中 0结束',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_scene` (`user_id`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话';

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`      BIGINT       NOT NULL,
  `role`            VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system/tool',
  `content`         MEDIUMTEXT            DEFAULT NULL,
  `tool_calls_json` MEDIUMTEXT            DEFAULT NULL,
  `refs_json`       MEDIUMTEXT            DEFAULT NULL COMMENT 'RAG引用等',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息';

CREATE TABLE IF NOT EXISTS `ai_knowledge_doc` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `title`            VARCHAR(256) NOT NULL,
  `domain`           VARCHAR(64)           DEFAULT 'general' COMMENT '售后/运营/商品等',
  `file_url`         VARCHAR(512)          DEFAULT NULL,
  `file_name`        VARCHAR(256)          DEFAULT NULL,
  `lightrag_doc_id`  VARCHAR(128)          DEFAULT NULL,
  `status`           VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending/indexing/ready/failed',
  `created_by`       BIGINT                DEFAULT NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据';

CREATE TABLE IF NOT EXISTS `ai_ticket` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`  BIGINT                DEFAULT NULL,
  `order_sn`    VARCHAR(64)           DEFAULT NULL,
  `intent`      VARCHAR(64)           DEFAULT NULL COMMENT '投诉/退款/物流/咨询',
  `priority`    VARCHAR(16)  NOT NULL DEFAULT 'medium',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'open' COMMENT 'open/processing/escalated/closed',
  `summary`     VARCHAR(512)          DEFAULT NULL,
  `assignee`    VARCHAR(64)           DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能工单';

CREATE TABLE IF NOT EXISTS `ai_ticket_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `ticket_id`   BIGINT       NOT NULL,
  `action`      VARCHAR(64)  NOT NULL,
  `detail`      VARCHAR(1024)         DEFAULT NULL,
  `operator`    VARCHAR(64)           DEFAULT 'system',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转日志';

CREATE TABLE IF NOT EXISTS `ai_insight_query` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT                DEFAULT NULL,
  `question`     VARCHAR(512) NOT NULL,
  `plan_json`    MEDIUMTEXT            DEFAULT NULL,
  `result_json`  MEDIUMTEXT            DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据洞察查询记录';

-- 前端可维护的 Chat / Embedding / LightRAG 配置
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL COMMENT '配置键，默认 default',
  `chat_provider` VARCHAR(32) NOT NULL DEFAULT 'dashscope' COMMENT 'dashscope/deepseek/openai/nvidia/custom',
  `chat_base_url` VARCHAR(512) DEFAULT NULL,
  `chat_api_key` VARCHAR(512) DEFAULT NULL,
  `chat_model` VARCHAR(128) NOT NULL DEFAULT 'qwen-plus',
  `chat_temperature` DECIMAL(4,2) DEFAULT 0.70,
  `embedding_provider` VARCHAR(32) NOT NULL DEFAULT 'nvidia' COMMENT 'nvidia/openai/dashscope/custom',
  `embedding_base_url` VARCHAR(512) DEFAULT 'https://integrate.api.nvidia.com/v1',
  `embedding_api_key` VARCHAR(512) DEFAULT NULL,
  `embedding_model` VARCHAR(128) NOT NULL DEFAULT 'nvidia/llama-nemotron-embed-1b-v2',
  `embedding_dim` INT DEFAULT 2048,
  `lightrag_base_url` VARCHAR(256) DEFAULT 'http://localhost:9621',
  `mock_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1=无Key时规则降级',
  `extra_json` MEDIUMTEXT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型与Embedding配置';

INSERT INTO ai_model_config (
  config_key, chat_provider, chat_base_url, chat_model,
  embedding_provider, embedding_base_url, embedding_model, embedding_dim,
  lightrag_base_url, mock_enabled
) VALUES (
  'default', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus',
  'nvidia', 'https://integrate.api.nvidia.com/v1', 'nvidia/llama-nemotron-embed-1b-v2', 2048,
  'http://localhost:9621', 1
) ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;
