-- AI 模型配置表（前端可维护 Chat LLM + Embedding）
USE `mall_ai`;

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
