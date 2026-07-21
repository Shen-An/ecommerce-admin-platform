import request from "@/utils/request";
import { AxiosPromise } from "axios";

export interface TicketChatPayload {
  sessionId?: number | null;
  message: string;
}

export interface AgentStep {
  name: string;
  status: string;
  detail?: string;
  durationMs?: number;
}

export interface TicketChatResult {
  sessionId: number;
  ticketId: number;
  reply: string;
  intent?: string;
  intentLabel?: string;
  confidence?: number;
  orderSn?: string;
  priority?: string;
  status?: string;
  escalated?: boolean;
  escalateReasons?: string[];
  policySource?: string;
  policySnippet?: string;
  steps?: AgentStep[];
  references?: Record<string, unknown>[];
}

export interface TicketLogItem {
  id: number;
  ticketId?: number;
  action: string;
  detail?: string;
  operator?: string;
  createdAt?: string;
}

export interface TicketItem {
  id: number;
  sessionId?: number;
  orderSn?: string;
  intent?: string;
  priority?: string;
  status?: string;
  summary?: string;
  assignee?: string;
  createdAt?: string;
  updatedAt?: string;
  logs?: TicketLogItem[];
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
  role: string;
  content?: string;
  createdAt?: string;
}

export function chatTicket(
  data: TicketChatPayload
): AxiosPromise<TicketChatResult> {
  return request({
    url: "/mall-ai/api/v1/ai/ticket/chat",
    method: "post",
    data,
  });
}

export function listTickets(
  status = "all"
): AxiosPromise<TicketItem[]> {
  return request({
    url: "/mall-ai/api/v1/ai/ticket/list",
    method: "get",
    params: { status },
  });
}

export function getTicket(id: number): AxiosPromise<TicketItem> {
  return request({
    url: `/mall-ai/api/v1/ai/ticket/${id}`,
    method: "get",
  });
}

export function escalateTicket(
  id: number,
  reason?: string
): AxiosPromise<TicketItem> {
  return request({
    url: `/mall-ai/api/v1/ai/ticket/${id}/escalate`,
    method: "post",
    data: { reason },
  });
}

export function closeTicket(
  id: number,
  reason?: string
): AxiosPromise<TicketItem> {
  return request({
    url: `/mall-ai/api/v1/ai/ticket/${id}/close`,
    method: "post",
    data: { reason },
  });
}

export function listTicketSessions(): AxiosPromise<AiSessionItem[]> {
  return request({
    url: "/mall-ai/api/v1/ai/ticket/sessions",
    method: "get",
  });
}

export function listTicketMessages(
  sessionId: number
): AxiosPromise<AiMessageItem[]> {
  return request({
    url: `/mall-ai/api/v1/ai/ticket/sessions/${sessionId}/messages`,
    method: "get",
  });
}
