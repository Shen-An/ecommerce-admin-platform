import request from "@/utils/request";
import { AxiosPromise } from "axios";

export interface InsightQueryPayload {
  question: string;
  template?: string;
}

export interface InsightQueryResult {
  queryId?: number;
  question?: string;
  templateCode?: string;
  templateLabel?: string;
  planReason?: string;
  params?: Record<string, unknown>;
  chartType?: string;
  option?: Record<string, unknown>;
  narrative?: string;
  metrics?: Record<string, unknown>;
  whitelist?: boolean;
  securityNote?: string;
  createdAt?: string;
}

export interface InsightHistoryItem {
  id: number;
  question?: string;
  templateCode?: string;
  templateLabel?: string;
  createdAt?: string;
}

export interface InsightTemplateItem {
  code: string;
  label: string;
}

export function queryInsight(
  data: InsightQueryPayload
): AxiosPromise<InsightQueryResult> {
  return request({
    url: "/mall-ai/api/v1/ai/insight/query",
    method: "post",
    data,
  });
}

export function listInsightHistory(
  limit = 20
): AxiosPromise<InsightHistoryItem[]> {
  return request({
    url: "/mall-ai/api/v1/ai/insight/history",
    method: "get",
    params: { limit },
  });
}

export function listInsightTemplates(): AxiosPromise<InsightTemplateItem[]> {
  return request({
    url: "/mall-ai/api/v1/ai/insight/templates",
    method: "get",
  });
}
