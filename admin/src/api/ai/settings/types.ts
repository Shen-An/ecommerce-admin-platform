/**
 * AI 模型配置 VO（后端脱敏返回）
 */
export interface AiModelConfigVO {
  configKey?: string;
  chatProvider?: string;
  chatBaseUrl?: string;
  chatApiKeyMasked?: string;
  chatApiKeyConfigured?: boolean;
  chatModel?: string;
  chatTemperature?: number;
  embeddingProvider?: string;
  embeddingBaseUrl?: string;
  embeddingApiKeyMasked?: string;
  embeddingApiKeyConfigured?: boolean;
  embeddingModel?: string;
  embeddingDim?: number;
  lightragBaseUrl?: string;
  mockEnabled?: number;
  extraJson?: string;
  chatProviders?: ProviderOption[];
  embeddingProviders?: ProviderOption[];
  chatPresets?: ModelPreset[];
  embeddingPresets?: ModelPreset[];
}

export interface ProviderOption {
  value: string;
  label: string;
  defaultBaseUrl?: string;
}

export interface ModelPreset {
  provider: string;
  model: string;
  label: string;
  embeddingDim?: number;
  baseUrl?: string;
}

/**
 * 保存表单：API Key 留空 = 不覆盖
 */
export interface AiModelConfigForm {
  configKey?: string;
  chatProvider: string;
  chatBaseUrl?: string;
  chatApiKey?: string;
  chatModel: string;
  chatTemperature?: number;
  embeddingProvider: string;
  embeddingBaseUrl?: string;
  embeddingApiKey?: string;
  embeddingModel: string;
  embeddingDim?: number;
  lightragBaseUrl?: string;
  mockEnabled?: number;
  extraJson?: string;
}

export interface AiConnectionTestResult {
  success: boolean;
  message?: string;
  model?: string;
  baseUrl?: string;
  detectedDim?: number;
  rawId?: string;
}
