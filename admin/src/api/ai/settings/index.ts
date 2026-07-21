import request from "@/utils/request";
import { AxiosPromise } from "axios";
import {
  AiModelConfigForm,
  AiModelConfigVO,
  AiConnectionTestResult,
} from "./types";

/**
 * 获取 AI 模型配置（密钥脱敏）
 */
export function getAiModelConfig(
  configKey = "default"
): AxiosPromise<AiModelConfigVO> {
  return request({
    url: "/mall-ai/api/v1/ai/settings",
    method: "get",
    params: { configKey },
  });
}

/**
 * 保存 AI 模型配置
 * API Key 留空表示不修改已有密钥
 */
export function saveAiModelConfig(data: AiModelConfigForm) {
  return request({
    url: "/mall-ai/api/v1/ai/settings",
    method: "put",
    data,
  });
}

/**
 * 连通性测试 type = chat | embedding | lightrag
 */
export function testAiConnection(
  type: "chat" | "embedding" | "lightrag",
  data?: Partial<AiModelConfigForm>
): AxiosPromise<AiConnectionTestResult> {
  return request({
    url: "/mall-ai/api/v1/ai/settings/test",
    method: "post",
    params: { type },
    data: data || {},
  });
}
