<!-- AI 运营助手（占位，后续接对话流） -->
<script setup lang="ts">
defineOptions({
  name: "AiAssistant",
  inheritAttrs: false,
});

import request from "@/utils/request";

const message = ref("今天订单量和待发货情况怎么样？");
const loading = ref(false);
const answer = ref("");
const intent = ref("");
const mock = ref(false);

function handleSend() {
  if (!message.value.trim()) {
    ElMessage.warning("请输入问题");
    return;
  }
  loading.value = true;
  answer.value = "";
  request({
    url: "/mall-ai/api/v1/ai/assistant/chat",
    method: "post",
    data: { message: message.value },
  })
    .then(({ data }) => {
      answer.value = data?.reply || JSON.stringify(data);
      intent.value = data?.intent || "";
      mock.value = !!data?.mock;
    })
    .finally(() => {
      loading.value = false;
    });
}
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>运营助手</span>
      </template>
      <el-alert
        class="mb-3"
        type="info"
        :closable="false"
        title="MVP 阶段支持规则路由 + Mock。配置模型请前往「模型配置」。"
      />
      <el-input
        v-model="message"
        type="textarea"
        :rows="4"
        placeholder="例如：近 7 日 GMV 怎么样？待发货订单有多少？"
      />
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="handleSend">
          发送
        </el-button>
      </div>
      <el-divider />
      <div v-if="answer" class="answer">
        <div class="meta">
          <el-tag v-if="mock" type="warning" size="small">Mock</el-tag>
          <el-tag v-if="intent" size="small" class="ml-1" type="info">
            {{ intent }}
          </el-tag>
        </div>
        <pre>{{ answer }}</pre>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.mb-3 {
  margin-bottom: 12px;
}
.actions {
  margin-top: 12px;
}
.answer pre {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
}
.meta {
  margin-bottom: 8px;
}
.ml-1 {
  margin-left: 4px;
}
</style>
