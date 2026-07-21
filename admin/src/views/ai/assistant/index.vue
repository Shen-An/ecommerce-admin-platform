<!-- AI 运营助手：多轮会话 + Tool cards -->
<script setup lang="ts">
defineOptions({
  name: "AiAssistant",
  inheritAttrs: false,
});

import {
  chatAssistant,
  listAssistantSessions,
  listAssistantMessages,
  deleteAssistantSession,
  type AiMessageItem,
  type AiSessionItem,
  type AssistantCard,
} from "@/api/ai/assistant";

interface ChatBubble {
  id?: number;
  role: "user" | "assistant";
  content: string;
  cards?: AssistantCard[];
  intent?: string;
  mock?: boolean;
}

const loading = ref(false);
const sessionLoading = ref(false);
const input = ref("查一下待发货订单");
const sessionId = ref<number | null>(null);
const sessions = ref<AiSessionItem[]>([]);
const bubbles = ref<ChatBubble[]>([]);
const chatBodyRef = ref<HTMLElement | null>(null);

const quickPrompts = [
  "查一下待发货订单",
  "智能音箱还有库存吗",
  "今天运营情况怎么样",
];

onMounted(() => {
  refreshSessions();
});

function refreshSessions() {
  sessionLoading.value = true;
  listAssistantSessions()
    .then(({ data }) => {
      sessions.value = data || [];
    })
    .finally(() => {
      sessionLoading.value = false;
    });
}

function handleNewChat() {
  sessionId.value = null;
  bubbles.value = [];
  input.value = "查一下待发货订单";
}

async function handleSelectSession(item: AiSessionItem) {
  if (!item?.id) return;
  sessionId.value = item.id;
  loading.value = true;
  try {
    const { data } = await listAssistantMessages(item.id);
    bubbles.value = (data || []).map((m: AiMessageItem) => ({
      id: m.id,
      role: m.role === "user" ? "user" : "assistant",
      content: m.content || "",
      cards: m.cards,
    }));
    await nextTick();
    scrollToBottom();
  } finally {
    loading.value = false;
  }
}

function handleDeleteSession(item: AiSessionItem, e: Event) {
  e.stopPropagation();
  if (!item?.id) return;
  ElMessageBox.confirm(`结束会话「${item.title || item.id}」？`, "提示", {
    type: "warning",
  })
    .then(() => deleteAssistantSession(item.id))
    .then(() => {
      if (sessionId.value === item.id) {
        handleNewChat();
      }
      refreshSessions();
      ElMessage.success("已结束会话");
    })
    .catch(() => undefined);
}

function applyQuick(prompt: string) {
  input.value = prompt;
  handleSend();
}

async function handleSend() {
  const text = input.value.trim();
  if (!text) {
    ElMessage.warning("请输入问题");
    return;
  }
  bubbles.value.push({ role: "user", content: text });
  input.value = "";
  loading.value = true;
  await nextTick();
  scrollToBottom();
  try {
    const { data } = await chatAssistant({
      sessionId: sessionId.value,
      message: text,
    });
    sessionId.value = data?.sessionId ?? sessionId.value;
    bubbles.value.push({
      role: "assistant",
      content: data?.reply || "",
      cards: data?.cards,
      intent: data?.intent,
      mock: data?.mock,
    });
    refreshSessions();
    await nextTick();
    scrollToBottom();
  } catch (err: any) {
    bubbles.value.push({
      role: "assistant",
      content: err?.message || "请求失败，请稍后重试",
    });
  } finally {
    loading.value = false;
  }
}

function scrollToBottom() {
  const el = chatBodyRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

function isOrderCard(card: AssistantCard) {
  return card?.type === "order";
}
function isProductCard(card: AssistantCard) {
  return card?.type === "product";
}
function isOpsCard(card: AssistantCard) {
  return card?.type === "ops_summary";
}

function formatTime(v?: string) {
  if (!v) return "";
  return String(v).replace("T", " ").slice(0, 16);
}
</script>

<template>
  <div class="assistant-layout">
    <!-- 会话侧栏 -->
    <el-card class="session-panel" shadow="never" :body-style="{ padding: '12px' }">
      <div class="session-header">
        <span class="title">会话</span>
        <el-button type="primary" link @click="handleNewChat">新对话</el-button>
      </div>
      <el-scrollbar height="calc(100vh - 220px)">
        <div v-loading="sessionLoading" class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: sessionId === s.id }"
            @click="handleSelectSession(s)"
          >
            <div class="session-title">{{ s.title || `会话 #${s.id}` }}</div>
            <div class="session-meta">
              <span>{{ formatTime(s.updatedAt) }}</span>
              <el-button
                type="danger"
                link
                size="small"
                @click="(e: Event) => handleDeleteSession(s, e)"
              >
                结束
              </el-button>
            </div>
          </div>
          <el-empty v-if="!sessionLoading && !sessions.length" description="暂无会话" :image-size="64" />
        </div>
      </el-scrollbar>
    </el-card>

    <!-- 对话主区 -->
    <el-card class="chat-panel" shadow="never" :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }">
      <div class="chat-header">
        <div>
          <span class="title">运营助手</span>
          <el-tag v-if="sessionId" size="small" class="ml-2" type="info">#{{ sessionId }}</el-tag>
        </div>
        <el-text type="info" size="small">
          Tool 查订单/商品/运营摘要；Mock 关闭且配置 Key 后可润色回复
        </el-text>
      </div>

      <div ref="chatBodyRef" class="chat-body">
        <div v-if="!bubbles.length" class="welcome">
          <p>你好，我是运营智能助手。试试：</p>
          <div class="quick">
            <el-tag
              v-for="p in quickPrompts"
              :key="p"
              class="quick-tag"
              effect="plain"
              round
              @click="applyQuick(p)"
            >
              {{ p }}
            </el-tag>
          </div>
        </div>

        <div v-for="(b, idx) in bubbles" :key="idx" class="bubble-row" :class="b.role">
          <div class="bubble">
            <div class="bubble-meta" v-if="b.role === 'assistant'">
              <el-tag v-if="b.mock" type="warning" size="small">规则/Tool</el-tag>
              <el-tag v-else type="success" size="small">LLM 润色</el-tag>
              <el-tag v-if="b.intent" size="small" type="info" class="ml-1">{{ b.intent }}</el-tag>
            </div>
            <div class="bubble-text">{{ b.content }}</div>

            <!-- 订单 cards -->
            <el-table
              v-if="b.cards?.some(isOrderCard)"
              :data="b.cards!.filter(isOrderCard)"
              size="small"
              class="card-table"
              border
            >
              <el-table-column prop="orderSn" label="订单号" min-width="150" />
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="amount" label="金额(元)" width="100" />
              <el-table-column prop="skuName" label="商品" min-width="120" show-overflow-tooltip />
              <el-table-column prop="totalQuantity" label="件数" width="70" />
            </el-table>

            <!-- 商品 cards -->
            <el-table
              v-if="b.cards?.some(isProductCard)"
              :data="b.cards!.filter(isProductCard)"
              size="small"
              class="card-table"
              border
            >
              <el-table-column prop="name" label="商品" min-width="160" show-overflow-tooltip />
              <el-table-column prop="price" label="价格(元)" width="100" />
              <el-table-column prop="stock" label="库存" width="80" />
              <el-table-column prop="sales" label="销量" width="80" />
              <el-table-column prop="categoryName" label="分类" width="100" />
              <el-table-column prop="status" label="状态" width="90" />
            </el-table>

            <!-- 运营摘要 card -->
            <div
              v-for="(c, ci) in (b.cards || []).filter(isOpsCard)"
              :key="'ops-' + ci"
              class="ops-card"
            >
              <el-descriptions :column="3" size="small" border>
                <el-descriptions-item label="日期">{{ c.date }}</el-descriptions-item>
                <el-descriptions-item label="待付款">{{ c.unpaid }}</el-descriptions-item>
                <el-descriptions-item label="待发货">{{ c.paid }}</el-descriptions-item>
                <el-descriptions-item label="已发货">{{ c.shipped }}</el-descriptions-item>
                <el-descriptions-item label="已完成">{{ c.complete }}</el-descriptions-item>
                <el-descriptions-item label="库存预警">{{ c.lowStock }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </div>
        <div v-if="loading" class="bubble-row assistant">
          <div class="bubble loading-bubble">思考中…</div>
        </div>
      </div>

      <div class="chat-footer">
        <div class="quick-inline">
          <el-button
            v-for="p in quickPrompts"
            :key="p"
            size="small"
            text
            @click="input = p"
          >
            {{ p }}
          </el-button>
        </div>
        <div class="composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="例如：查一下待发货订单 / 智能音箱还有库存吗"
            @keydown.ctrl.enter="handleSend"
          />
          <el-button type="primary" :loading="loading" class="send-btn" @click="handleSend">
            发送
          </el-button>
        </div>
        <div class="hint">Ctrl + Enter 发送</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.assistant-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 12px;
  height: calc(100vh - 120px);
  min-height: 560px;
}
.session-panel,
.chat-panel {
  height: 100%;
}
.session-header,
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.title {
  font-weight: 600;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.session-item {
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  background: var(--el-fill-color-blank);
}
.session-item:hover {
  background: var(--el-fill-color-light);
}
.session-item.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.session-title {
  font-size: 13px;
  line-height: 1.4;
  word-break: break-all;
}
.session-meta {
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--el-bg-color-page);
}
.welcome {
  color: var(--el-text-color-secondary);
  text-align: center;
  margin-top: 48px;
}
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 12px;
}
.quick-tag {
  cursor: pointer;
}
.bubble-row {
  display: flex;
  margin-bottom: 14px;
}
.bubble-row.user {
  justify-content: flex-end;
}
.bubble-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: min(780px, 92%);
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}
.bubble-row.user .bubble {
  background: var(--el-color-primary-light-9);
}
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
  font-size: 14px;
}
.bubble-meta {
  margin-bottom: 6px;
}
.card-table {
  margin-top: 10px;
  width: 100%;
}
.ops-card {
  margin-top: 10px;
}
.loading-bubble {
  color: var(--el-text-color-secondary);
}
.chat-footer {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 10px 16px 12px;
  background: var(--el-bg-color);
}
.quick-inline {
  margin-bottom: 6px;
}
.composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
}
.send-btn {
  height: 72px;
}
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ml-1 {
  margin-left: 4px;
}
.ml-2 {
  margin-left: 8px;
}
@media (max-width: 960px) {
  .assistant-layout {
    grid-template-columns: 1fr;
    height: auto;
  }
  .session-panel {
    height: 220px;
  }
}
</style>
