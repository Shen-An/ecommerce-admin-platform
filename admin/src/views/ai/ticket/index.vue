<!-- AI 工单多 Agent：意图 → 政策 RAG → 建单 → 人工接管 -->
<script setup lang="ts">
defineOptions({
  name: "AiTicket",
  inheritAttrs: false,
});

import {
  chatTicket,
  listTickets,
  getTicket,
  escalateTicket,
  closeTicket,
  listTicketSessions,
  listTicketMessages,
  type AgentStep,
  type AiSessionItem,
  type TicketChatResult,
  type TicketItem,
} from "@/api/ai/ticket";

interface ChatBubble {
  role: "user" | "assistant";
  content: string;
  result?: TicketChatResult;
}

const loading = ref(false);
const sessionLoading = ref(false);
const ticketLoading = ref(false);
const input = ref(
  "订单号 202401150001 物流停滞三天了，还说要起诉你们，怎么处理？"
);
const sessionId = ref<number | null>(null);
const currentTicketId = ref<number | null>(null);
const sessions = ref<AiSessionItem[]>([]);
const tickets = ref<TicketItem[]>([]);
const ticketFilter = ref("all");
const bubbles = ref<ChatBubble[]>([]);
const detail = ref<TicketItem | null>(null);
const chatBodyRef = ref<HTMLElement | null>(null);
const lastSteps = ref<AgentStep[]>([]);

const quickPrompts = [
  "订单号 202401150001 物流停滞三天了，还说要起诉你们，怎么处理？",
  "我想退货退款，商品刚收到不想要了，订单 202401150002",
  "咨询一下 7 天无理由退货怎么操作？",
  "你们发的是假货！差评！投诉！",
];

const statusTagType: Record<string, string> = {
  open: "primary",
  processing: "warning",
  escalated: "danger",
  closed: "info",
};

const stepStatusType: Record<string, string> = {
  ok: "success",
  degraded: "warning",
  escalate: "danger",
  pass: "success",
};

onMounted(() => {
  refreshSessions();
  refreshTickets();
});

function refreshSessions() {
  sessionLoading.value = true;
  listTicketSessions()
    .then(({ data }) => {
      sessions.value = data || [];
    })
    .finally(() => {
      sessionLoading.value = false;
    });
}

function refreshTickets() {
  ticketLoading.value = true;
  listTickets(ticketFilter.value)
    .then(({ data }) => {
      tickets.value = data || [];
    })
    .finally(() => {
      ticketLoading.value = false;
    });
}

function handleNewChat() {
  sessionId.value = null;
  currentTicketId.value = null;
  bubbles.value = [];
  lastSteps.value = [];
  detail.value = null;
  input.value = quickPrompts[0];
}

async function handleSelectSession(item: AiSessionItem) {
  if (!item?.id) return;
  sessionId.value = item.id;
  loading.value = true;
  try {
    const { data } = await listTicketMessages(item.id);
    bubbles.value = (data || []).map((m) => ({
      role: m.role === "user" ? "user" : "assistant",
      content: m.content || "",
    }));
    await nextTick();
    scrollToBottom();
  } finally {
    loading.value = false;
  }
}

function applyQuick(prompt: string) {
  input.value = prompt;
  handleSend();
}

async function handleSend() {
  const message = input.value?.trim();
  if (!message || loading.value) return;
  bubbles.value.push({ role: "user", content: message });
  input.value = "";
  loading.value = true;
  await nextTick();
  scrollToBottom();
  try {
    const { data } = await chatTicket({
      sessionId: sessionId.value,
      message,
    });
    sessionId.value = data.sessionId;
    currentTicketId.value = data.ticketId;
    lastSteps.value = data.steps || [];
    bubbles.value.push({
      role: "assistant",
      content: data.reply,
      result: data,
    });
    refreshSessions();
    refreshTickets();
    if (data.ticketId) {
      loadDetail(data.ticketId);
    }
    await nextTick();
    scrollToBottom();
  } catch (e: any) {
    bubbles.value.push({
      role: "assistant",
      content: e?.message || "请求失败，请检查 mall-ai 服务",
    });
  } finally {
    loading.value = false;
  }
}

function loadDetail(id: number) {
  getTicket(id)
    .then(({ data }) => {
      detail.value = data;
      currentTicketId.value = data.id;
    })
    .catch(() => {
      detail.value = null;
    });
}

function handleSelectTicket(row: TicketItem) {
  loadDetail(row.id);
  if (row.sessionId) {
    handleSelectSession({ id: row.sessionId });
  }
}

function handleEscalate() {
  if (!currentTicketId.value) {
    ElMessage.warning("请先创建或选择工单");
    return;
  }
  ElMessageBox.prompt("接管说明（可选）", "人工接管", {
    confirmButtonText: "确认接管",
    cancelButtonText: "取消",
    inputPlaceholder: "已介入处理…",
  })
    .then(({ value }) => escalateTicket(currentTicketId.value!, value || "人工接管"))
    .then(({ data }) => {
      detail.value = data;
      ElMessage.success("已升级为 escalated");
      refreshTickets();
    })
    .catch(() => undefined);
}

function handleClose() {
  if (!currentTicketId.value) {
    ElMessage.warning("请先选择工单");
    return;
  }
  ElMessageBox.confirm("确认关闭该工单？", "关闭工单", { type: "warning" })
    .then(() => closeTicket(currentTicketId.value!, "人工关闭"))
    .then(({ data }) => {
      detail.value = data;
      ElMessage.success("工单已关闭");
      refreshTickets();
    })
    .catch(() => undefined);
}

function scrollToBottom() {
  const el = chatBodyRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function intentLabel(intent?: string) {
  const map: Record<string, string> = {
    complaint: "投诉",
    refund: "退款",
    logistics: "物流",
    consult: "咨询",
    other: "其他",
  };
  return intent ? map[intent] || intent : "-";
}

function formatMd(text: string) {
  if (!text) return "";
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\n/g, "<br/>");
}
</script>

<template>
  <div class="app-container ticket-page">
    <el-row :gutter="12">
      <!-- 会话 -->
      <el-col :xs="24" :md="4">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hd">
              <span>会话</span>
              <el-button type="primary" link @click="handleNewChat">新建</el-button>
            </div>
          </template>
          <el-scrollbar height="520px" v-loading="sessionLoading">
            <div
              v-for="s in sessions"
              :key="s.id"
              class="session-item"
              :class="{ active: sessionId === s.id }"
              @click="handleSelectSession(s)"
            >
              <div class="title">{{ s.title || `会话 #${s.id}` }}</div>
              <div class="meta">#{{ s.id }}</div>
            </div>
            <el-empty v-if="!sessions.length" description="暂无会话" :image-size="48" />
          </el-scrollbar>
        </el-card>
      </el-col>

      <!-- 对话 + 步骤 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel chat-panel">
          <template #header>
            <div class="panel-hd">
              <span>工单 Agent 流水线</span>
              <div>
                <el-button
                  type="danger"
                  size="small"
                  :disabled="!currentTicketId"
                  @click="handleEscalate"
                >
                  人工接管
                </el-button>
                <el-button
                  size="small"
                  :disabled="!currentTicketId"
                  @click="handleClose"
                >
                  关闭工单
                </el-button>
              </div>
            </div>
          </template>

          <div class="quick">
            <el-tag
              v-for="(p, i) in quickPrompts"
              :key="i"
              class="qtag"
              effect="plain"
              @click="applyQuick(p)"
            >
              {{ p.length > 28 ? p.slice(0, 28) + "…" : p }}
            </el-tag>
          </div>

          <div ref="chatBodyRef" class="chat-body" v-loading="loading">
            <div v-if="!bubbles.length" class="hint">
              输入投诉/退款/物流话术（可带订单号），流水线将执行：Intent → PolicyRAG → Escalation → Ticket
            </div>
            <div
              v-for="(b, idx) in bubbles"
              :key="idx"
              class="bubble"
              :class="b.role"
            >
              <div class="role">{{ b.role === "user" ? "用户" : "Agent" }}</div>
              <div class="content" v-html="formatMd(b.content)" />
              <div v-if="b.result" class="meta-row">
                <el-tag size="small">{{ b.result.intentLabel || b.result.intent }}</el-tag>
                <el-tag
                  size="small"
                  :type="(statusTagType[b.result.status || ''] as any) || 'info'"
                  class="ml8"
                >
                  {{ b.result.status }}
                </el-tag>
                <el-tag v-if="b.result.escalated" size="small" type="danger" class="ml8">
                  已升级
                </el-tag>
                <span v-if="b.result.ticketId" class="ml8 muted">#{{ b.result.ticketId }}</span>
              </div>
            </div>
          </div>

          <div v-if="lastSteps.length" class="steps">
            <div class="steps-title">Agent 步骤</div>
            <el-steps :active="lastSteps.length" align-center finish-status="success">
              <el-step
                v-for="(st, i) in lastSteps"
                :key="i"
                :title="st.name"
                :description="(st.detail || '').slice(0, 48)"
                :status="st.status === 'escalate' ? 'error' : st.status === 'degraded' ? 'wait' : 'success'"
              />
            </el-steps>
            <el-timeline class="step-timeline">
              <el-timeline-item
                v-for="(st, i) in lastSteps"
                :key="'t' + i"
                :type="(stepStatusType[st.status] as any) || 'primary'"
                :timestamp="st.durationMs != null ? st.durationMs + ' ms' : ''"
              >
                <strong>{{ st.name }}</strong>
                <span class="muted"> · {{ st.status }}</span>
                <div class="step-detail">{{ st.detail }}</div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <div class="composer">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              placeholder="输入客诉话术…"
              @keydown="onKeydown"
            />
            <el-button type="primary" :loading="loading" @click="handleSend">
              发送并建单
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 工单列表 + 详情 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hd">
              <span>工单列表</span>
              <el-select
                v-model="ticketFilter"
                size="small"
                style="width: 120px"
                @change="refreshTickets"
              >
                <el-option label="全部" value="all" />
                <el-option label="open" value="open" />
                <el-option label="escalated" value="escalated" />
                <el-option label="closed" value="closed" />
              </el-select>
            </div>
          </template>
          <el-table
            v-loading="ticketLoading"
            :data="tickets"
            size="small"
            height="240"
            highlight-current-row
            @row-click="handleSelectTicket"
          >
            <el-table-column prop="id" label="ID" width="56" />
            <el-table-column label="意图" width="72">
              <template #default="{ row }">{{ intentLabel(row.intent) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="(statusTagType[row.status] as any) || 'info'">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="orderSn" label="订单号" min-width="100" show-overflow-tooltip />
          </el-table>
        </el-card>

        <el-card shadow="never" class="panel detail-panel">
          <template #header>
            <span>工单详情 {{ detail ? "#" + detail.id : "" }}</span>
          </template>
          <template v-if="detail">
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="意图">
                {{ intentLabel(detail.intent) }}
              </el-descriptions-item>
              <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag size="small" :type="(statusTagType[detail.status || ''] as any) || 'info'">
                  {{ detail.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="订单号">{{ detail.orderSn || "-" }}</el-descriptions-item>
              <el-descriptions-item label="处理人">{{ detail.assignee || "-" }}</el-descriptions-item>
              <el-descriptions-item label="摘要">{{ detail.summary }}</el-descriptions-item>
            </el-descriptions>
            <div class="log-title">流转日志</div>
            <el-timeline>
              <el-timeline-item
                v-for="log in detail.logs || []"
                :key="log.id"
                :timestamp="log.createdAt"
                placement="top"
              >
                <strong>{{ log.action }}</strong>
                <span class="muted"> · {{ log.operator }}</span>
                <div class="step-detail">{{ log.detail }}</div>
              </el-timeline-item>
            </el-timeline>
          </template>
          <el-empty v-else description="选择或创建工单" :image-size="56" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.ticket-page {
  .panel {
    margin-bottom: 12px;
  }
  .panel-hd {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .session-item {
    padding: 8px 10px;
    border-radius: 6px;
    cursor: pointer;
    margin-bottom: 4px;
    &:hover,
    &.active {
      background: var(--el-color-primary-light-9);
    }
    .title {
      font-size: 13px;
      line-height: 1.3;
    }
    .meta {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
  .quick {
    margin-bottom: 8px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    .qtag {
      cursor: pointer;
      max-width: 100%;
    }
  }
  .chat-body {
    height: 280px;
    overflow-y: auto;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-fill-color-blank);
  }
  .hint {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.6;
  }
  .bubble {
    margin-bottom: 12px;
    max-width: 92%;
    &.user {
      margin-left: auto;
      .content {
        background: var(--el-color-primary-light-9);
      }
    }
    &.assistant .content {
      background: var(--el-fill-color-light);
    }
    .role {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-bottom: 4px;
    }
    .content {
      padding: 8px 12px;
      border-radius: 8px;
      font-size: 13px;
      line-height: 1.55;
      word-break: break-word;
    }
    .meta-row {
      margin-top: 6px;
    }
  }
  .steps {
    margin-top: 12px;
    padding-top: 8px;
    border-top: 1px dashed var(--el-border-color);
  }
  .steps-title {
    font-weight: 600;
    margin-bottom: 8px;
  }
  .step-timeline {
    margin-top: 12px;
    max-height: 160px;
    overflow-y: auto;
  }
  .step-detail {
    font-size: 12px;
    color: var(--el-text-color-regular);
    margin-top: 2px;
    word-break: break-all;
  }
  .composer {
    margin-top: 12px;
    display: flex;
    gap: 8px;
    align-items: flex-end;
    .el-textarea {
      flex: 1;
    }
  }
  .detail-panel {
    min-height: 280px;
  }
  .log-title {
    margin: 12px 0 8px;
    font-weight: 600;
  }
  .muted {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  .ml8 {
    margin-left: 8px;
  }
}
</style>
