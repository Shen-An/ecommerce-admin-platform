import request from "@/utils/request";
import { AxiosPromise } from "axios";

export interface KnowledgeDoc {
  id: number;
  title?: string;
  domain?: string;
  fileName?: string;
  fileUrl?: string;
  lightragDocId?: string;
  status?: string;
  errorMsg?: string;
  contentLength?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface KnowledgeQueryResult {
  answer?: string;
  mode?: string;
  source?: string;
  degraded?: boolean;
  references?: Array<Record<string, any>>;
  hint?: string;
}

export interface KnowledgeStatus {
  engine?: string;
  embedding?: string;
  chat?: string;
  embeddingModel?: string;
  chunkCount?: number;
  embeddedChunkCount?: number;
  lightrag?: string;
  baseUrl?: string;
  localDocCount?: number;
  indexingCount?: number;
  readyCount?: number;
  localOnlyCount?: number;
  failedCount?: number;
  pipeline?: Record<string, unknown>;
  hint?: string;
}

export function getKnowledgeStatus(): AxiosPromise<KnowledgeStatus> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/status",
    method: "get",
  });
}

export function listKnowledgeDocs(): AxiosPromise<KnowledgeDoc[]> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs",
    method: "get",
  });
}

export function ingestKnowledgeText(data: {
  title: string;
  domain?: string;
  content: string;
}): AxiosPromise<KnowledgeDoc> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs/text",
    method: "post",
    data,
  });
}

export function uploadKnowledgeFile(form: FormData): AxiosPromise<KnowledgeDoc> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs/upload",
    method: "post",
    data: form,
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export function deleteKnowledgeDoc(id: number) {
  return request({
    url: `/mall-ai/api/v1/ai/knowledge/docs/${id}`,
    method: "delete",
  });
}

export function seedKnowledgeDocs(): AxiosPromise<{ created: number; message: string }> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs/seed",
    method: "post",
  });
}

/** 重建 Java 向量索引（Key 来自模型配置） */
export function reindexKnowledgeDocs(): AxiosPromise<{
  pushed: number;
  indexed?: number;
  message: string;
}> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs/reindex",
    method: "post",
  });
}

export function refreshKnowledgeIndexStatus(): AxiosPromise<{
  changed: number;
  message: string;
}> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/docs/refresh-status",
    method: "post",
  });
}

export function queryKnowledge(data: {
  question: string;
  mode?: string;
}): AxiosPromise<KnowledgeQueryResult> {
  return request({
    url: "/mall-ai/api/v1/ai/knowledge/query",
    method: "post",
    data,
  });
}
