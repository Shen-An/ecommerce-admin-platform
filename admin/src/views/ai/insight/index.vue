<!-- AI 数据洞察：白名单模板 + ECharts -->
<script setup lang="ts">
defineOptions({
  name: "AiInsight",
  inheritAttrs: false,
});

import * as echarts from "echarts";
import {
  queryInsight,
  listInsightHistory,
  listInsightTemplates,
  type InsightHistoryItem,
  type InsightQueryResult,
  type InsightTemplateItem,
} from "@/api/ai/insight";

const loading = ref(false);
const question = ref("近 7 天商品销量 Top5");
const forcedTemplate = ref<string>("");
const result = ref<InsightQueryResult | null>(null);
const history = ref<InsightHistoryItem[]>([]);
const templates = ref<InsightTemplateItem[]>([]);
const chartRef = ref<HTMLElement | null>(null);
let chart: echarts.ECharts | null = null;

const quickQuestions = [
  "近 7 天商品销量 Top5",
  "品类销量分布",
  "GMV 成交额快照",
  "订单状态分布怎么样",
  "库存预警有哪些商品",
  "取消和售后占比如何",
  "今天运营综合看板",
];

onMounted(() => {
  listInsightTemplates()
    .then(({ data }) => {
      templates.value = data || [];
    })
    .catch(() => undefined);
  refreshHistory();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chart?.dispose();
  chart = null;
});

function refreshHistory() {
  listInsightHistory(20)
    .then(({ data }) => {
      history.value = data || [];
    })
    .catch(() => {
      history.value = [];
    });
}

function applyQuick(q: string) {
  question.value = q;
  handleQuery();
}

async function handleQuery() {
  const q = question.value?.trim();
  if (!q || loading.value) return;
  loading.value = true;
  try {
    const { data } = await queryInsight({
      question: q,
      template: forcedTemplate.value || undefined,
    });
    result.value = data;
    await nextTick();
    renderChart(data?.option);
    refreshHistory();
  } catch (e: any) {
    ElMessage.error(e?.message || "洞察请求失败");
  } finally {
    loading.value = false;
  }
}

function renderChart(option?: Record<string, unknown>) {
  if (!chartRef.value) return;
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  if (!option || Object.keys(option).length === 0) {
    chart.clear();
    return;
  }
  chart.setOption(option as echarts.EChartsOption, true);
}

function handleResize() {
  chart?.resize();
}

function onHistoryClick(item: InsightHistoryItem) {
  if (item.question) {
    question.value = item.question;
    handleQuery();
  }
}
</script>

<template>
  <div class="app-container insight-page">
    <el-row :gutter="12">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hd">
              <span>数据洞察 Agent</span>
              <el-tag type="success" size="small" effect="plain">白名单查询 · 无 SQL 注入</el-tag>
            </div>
          </template>

          <div class="quick">
            <el-tag
              v-for="(q, i) in quickQuestions"
              :key="i"
              class="qtag"
              effect="plain"
              @click="applyQuick(q)"
            >
              {{ q }}
            </el-tag>
          </div>

          <div class="composer">
            <el-input
              v-model="question"
              type="textarea"
              :rows="2"
              placeholder="用自然语言提问，例如：销量 Top5 / 库存预警 / 订单状态分布"
              @keydown.enter.exact.prevent="handleQuery"
            />
            <div class="composer-actions">
              <el-select
                v-model="forcedTemplate"
                clearable
                placeholder="可选：指定模板"
                style="width: 180px"
              >
                <el-option
                  v-for="t in templates"
                  :key="t.code"
                  :label="t.label"
                  :value="t.code"
                />
              </el-select>
              <el-button type="primary" :loading="loading" @click="handleQuery">
                分析
              </el-button>
            </div>
          </div>

          <div v-if="result" class="result-meta">
            <el-tag size="small">{{ result.templateLabel || result.templateCode }}</el-tag>
            <el-tag size="small" type="info" class="ml8">{{ result.chartType }}</el-tag>
            <el-tag v-if="result.whitelist" size="small" type="success" class="ml8">whitelist</el-tag>
            <span class="muted ml8">{{ result.planReason }}</span>
          </div>

          <div ref="chartRef" class="chart-box" v-loading="loading" />

          <el-alert
            v-if="result?.narrative"
            :title="result.narrative"
            type="info"
            :closable="false"
            show-icon
            class="narrative"
          />
          <el-alert
            v-if="result?.securityNote"
            :title="result.securityNote"
            type="success"
            :closable="false"
            class="sec-note"
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hd">
              <span>白名单模板</span>
            </div>
          </template>
          <el-table :data="templates" size="small" max-height="200">
            <el-table-column prop="code" label="Code" width="140" />
            <el-table-column prop="label" label="说明" />
          </el-table>
          <p class="hint">
            QueryPlanner 将自然语言映射到上述模板，经 Feign 只读聚合 OMS/PMS 数据，前端用 ECharts 渲染。
          </p>
        </el-card>

        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hd">
              <span>历史查询</span>
              <el-button link type="primary" @click="refreshHistory">刷新</el-button>
            </div>
          </template>
          <el-scrollbar height="320px">
            <div
              v-for="h in history"
              :key="h.id"
              class="hist-item"
              @click="onHistoryClick(h)"
            >
              <div class="title">{{ h.question }}</div>
              <div class="meta">
                {{ h.templateLabel || h.templateCode || "-" }} · #{{ h.id }}
              </div>
            </div>
            <el-empty v-if="!history.length" description="暂无记录" :image-size="48" />
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.insight-page {
  .panel {
    margin-bottom: 12px;
  }
  .panel-hd {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .quick {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;
    .qtag {
      cursor: pointer;
    }
  }
  .composer {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .composer-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
  .result-meta {
    margin: 12px 0 8px;
  }
  .chart-box {
    width: 100%;
    height: 360px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
  }
  .narrative {
    margin-top: 12px;
  }
  .sec-note {
    margin-top: 8px;
  }
  .hint {
    margin-top: 10px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
  }
  .hist-item {
    padding: 8px 10px;
    border-radius: 6px;
    cursor: pointer;
    margin-bottom: 4px;
    &:hover {
      background: var(--el-color-primary-light-9);
    }
    .title {
      font-size: 13px;
      line-height: 1.35;
    }
    .meta {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
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
