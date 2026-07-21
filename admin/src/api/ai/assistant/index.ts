import request from "@/utils/request";
import { AxiosPromise } from "axios";

export interface AssistantChatPayload {
  sessionId?: number | null;
  message: string;
}

export interface AssistantCard {
  type?: string;
  orderSn?: string;
  status?: string;
  amount?: string | number;
  totalQuantity?: number;
  skuName?: string;
  spuId?: number;
  name?: string;
  price?: string | number;
  stock?: number | null;
  sales?: number;
  categoryName?: string;
  brandName?: string;
  date?: string;
  unpaid?: number;
  paid?: number;
  shipped?: number;
  complete?: number;
  canceled?: number;
  servicing?: number;
  lowStock?: number;
  [key: string]: unknown;
}

export interface AssistantChatResult {
  sessionId: number;
  reply: string;
  intent?: string;
  cards?: AssistantCard[];
  mock?: boolean;
}

export interface AiSessionItem {
  id: number;
  scene?: string;
  title?: string;
  status?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiMessageItem {
  id: number;
  role: "user" | "assistant" | "system" | "tool" | string;
  content?: string;
  cards?: AssistantCard[];
  createdAt?: string;
}

/** 运营助手对话 */
export function chatAssistant(
  data: AssistantChatPayload
): AxiosPromise<AssistantChatResult> {
  return request({
    url: "/mall-ai/api/v1/ai/assistant/chat",
    method: "post",
    data,
  });
}

/** 会话列表 */
export function listAssistantSessions(): AxiosPromise<AiSessionItem[]> {
  return request({
    url: "/mall-ai/api/v1/ai/assistant/sessions",
    method: "get",
  });
}

/** 历史消息 */
export function listAssistantMessages(
  sessionId: number
): AxiosPromise<AiMessageItem[]> {
  return request({
    url: `/mall-ai/api/v1/ai/assistant/sessions/${sessionId}/messages`,
    method: "get",
  });
}

/** 删除/结束会话 */
export function deleteAssistantSession(sessionId: number) {
  return request({
    url: `/mall-ai/api/v1/ai/assistant/sessions/${sessionId}`,
    method: "delete",
  });
}
