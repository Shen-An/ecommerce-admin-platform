<!-- AI 知识库（占位，后续接 LightRAG） -->
<script setup lang="ts">
defineOptions({
  name: "AiKnowledge",
  inheritAttrs: false,
});

import request from "@/utils/request";

const query = ref("退货退款政策是什么？");
const loading = ref(false);
const result = ref("");
const health = ref<any>(null);

function handleQuery() {
  loading.value = true;
  request({
    url: "/mall-ai/api/v1/ai/knowledge/query",
    method: "post",
    data: { question: query.value },
  })
    .then(({ data }) => {
      result.value =
        typeof data === "string" ? data : JSON.stringify(data, null, 2);
    })
    .catch(() => {
      result.value = "";
    })
    .finally(() => {
      loading.value = false;
    });
}

function loadHealth() {
  request({
    url: "/mall-ai/api/v1/ai/health",
    method: "get",
  })
    .then(({ data }) => {
      health.value = data;
    })
    .catch(() => {
      health.value = { status: "unavailable" };
    });
}

onMounted(loadHealth);
</script>

<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-3">
      <template #header>
        <span>知识库 / LightRAG</span>
      </template>
      <el-alert
        type="info"
        :closable="false"
        title="Embedding 使用「模型配置」中的 nvidia/llama-nemotron-embed-1b-v2。请先配置 NVIDIA API Key，再启动 LightRAG 服务。"
      />
      <div v-if="health" class="health">
        服务健康：
        <el-tag size="small">{{ health.status || "ok" }}</el-tag>
        <span v-if="health.service" class="ml-2">{{ health.service }}</span>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-input
        v-model="query"
        type="textarea"
        :rows="3"
        placeholder="输入知识库问题"
      />
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="handleQuery">
          检索问答
        </el-button>
      </div>
      <pre v-if="result" class="result">{{ result }}</pre>
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
.health {
  margin-top: 12px;
}
.ml-2 {
  margin-left: 8px;
}
.result {
  margin-top: 16px;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
}
</style>
