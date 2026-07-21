<!-- AI 知识库：文档入库 + Java 向量 RAG / 关键词降级 -->
<script setup lang="ts">
defineOptions({
  name: "AiKnowledge",
  inheritAttrs: false,
});

import {
  getKnowledgeStatus,
  listKnowledgeDocs,
  ingestKnowledgeText,
  uploadKnowledgeFile,
  deleteKnowledgeDoc,
  seedKnowledgeDocs,
  reindexKnowledgeDocs,
  refreshKnowledgeIndexStatus,
  queryKnowledge,
  type KnowledgeDoc,
  type KnowledgeQueryResult,
  type KnowledgeStatus,
} from "@/api/ai/knowledge";

const status = ref<KnowledgeStatus | null>(null);
const docs = ref<KnowledgeDoc[]>([]);
const loadingDocs = ref(false);
const seeding = ref(false);
const reindexing = ref(false);
const refreshingIndex = ref(false);

const question = ref("7 天无理由退货怎么处理？");
const mode = ref("mix");
const querying = ref(false);
const answer = ref<KnowledgeQueryResult | null>(null);

const textDialog = ref(false);
const textForm = reactive({
  title: "",
  domain: "售后",
  content: "",
});
const savingText = ref(false);

const uploadDialog = ref(false);
const uploadDomain = ref("general");
const uploadTitle = ref("");
const fileList = ref<any[]>([]);
const uploading = ref(false);

const quickQuestions = [
  "7 天无理由退货怎么处理？",
  "质量问题多久可以退换？",
  "每日开店要检查哪些事项？",
  "物流停滞超 72 小时怎么办？",
];

onMounted(() => {
  refreshAll();
});

function refreshAll() {
  loadStatus();
  loadDocs();
}

function loadStatus() {
  getKnowledgeStatus()
    .then(({ data }) => {
      status.value = data;
    })
    .catch(() => {
      status.value = { lightrag: "DOWN", hint: "状态接口不可用" };
    });
}

function loadDocs() {
  loadingDocs.value = true;
  listKnowledgeDocs()
    .then(({ data }) => {
      docs.value = data || [];
    })
    .finally(() => {
      loadingDocs.value = false;
    });
}

function handleSeed() {
  seeding.value = true;
  seedKnowledgeDocs()
    .then(({ data }) => {
      ElMessage.success(data?.message || "完成");
      refreshAll();
    })
    .finally(() => {
      seeding.value = false;
    });
}

function handleReindex() {
  reindexing.value = true;
  reindexKnowledgeDocs()
    .then(({ data }) => {
      ElMessage.success(data?.message || "索引完成");
      refreshAll();
    })
    .catch((e: any) => {
      ElMessage.error(e?.message || "重建失败：请在模型配置填写 Embedding Key");
    })
    .finally(() => {
      reindexing.value = false;
    });
}

function handleRefreshIndex() {
  refreshingIndex.value = true;
  refreshKnowledgeIndexStatus()
    .then(({ data }) => {
      ElMessage.success(data?.message || "已刷新");
      refreshAll();
    })
    .finally(() => {
      refreshingIndex.value = false;
    });
}

function openTextDialog() {
  textForm.title = "";
  textForm.domain = "售后";
  textForm.content = "";
  textDialog.value = true;
}

function submitText() {
  if (!textForm.title.trim() || !textForm.content.trim()) {
    ElMessage.warning("标题与正文不能为空");
    return;
  }
  savingText.value = true;
  ingestKnowledgeText({ ...textForm })
    .then(() => {
      ElMessage.success("已入库");
      textDialog.value = false;
      refreshAll();
    })
    .finally(() => {
      savingText.value = false;
    });
}

function openUploadDialog() {
  uploadDomain.value = "general";
  uploadTitle.value = "";
  fileList.value = [];
  uploadDialog.value = true;
}

function handleUpload() {
  const raw = fileList.value[0]?.raw;
  if (!raw) {
    ElMessage.warning("请选择文件");
    return;
  }
  const fd = new FormData();
  fd.append("file", raw);
  fd.append("domain", uploadDomain.value || "general");
  if (uploadTitle.value) {
    fd.append("title", uploadTitle.value);
  }
  uploading.value = true;
  uploadKnowledgeFile(fd)
    .then(() => {
      ElMessage.success("上传成功");
      uploadDialog.value = false;
      refreshAll();
    })
    .finally(() => {
      uploading.value = false;
    });
}

function handleDelete(row: KnowledgeDoc) {
  ElMessageBox.confirm(`删除文档「${row.title}」？`, "提示", { type: "warning" })
    .then(() => deleteKnowledgeDoc(row.id))
    .then(() => {
      ElMessage.success("已删除");
      refreshAll();
    })
    .catch(() => undefined);
}

function applyQuestion(q: string) {
  question.value = q;
  handleQuery();
}

function handleQuery() {
  if (!question.value.trim()) {
    ElMessage.warning("请输入问题");
    return;
  }
  querying.value = true;
  answer.value = null;
  queryKnowledge({ question: question.value, mode: mode.value })
    .then(({ data }) => {
      answer.value = data;
    })
    .finally(() => {
      querying.value = false;
    });
}

function statusTagType(s?: string) {
  if (s === "ready" || s === "indexing") return "success";
  if (s === "local") return "warning";
  if (s === "failed") return "danger";
  return "info";
}

function formatRefContent(ref: Record<string, any>) {
  const c = ref.content;
  if (Array.isArray(c)) return c.join("\n");
  if (typeof c === "string") return c;
  return "";
}
</script>

<template>
  <div class="knowledge-page">
    <el-card shadow="never" class="mb-3">
      <template #header>
        <div class="card-header">
          <span>知识库 / Java RAG</span>
          <div>
            <el-button size="small" @click="refreshAll">刷新状态</el-button>
            <el-button size="small" type="success" :loading="seeding" @click="handleSeed">
              灌入演示语料
            </el-button>
            <el-button size="small" type="primary" :loading="reindexing" @click="handleReindex">
              重建 Java 向量索引
            </el-button>
          </div>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        title="真 RAG 在 mall-ai 内完成：模型配置页填 Embedding（+可选 Chat）→ 灌入语料 / 重建索引 → 问答 source=java_rag。无需 Python LightRAG .env。"
      />
      <div v-if="status" class="status-row">
        <el-tag
          :type="status.embedding === 'READY' ? 'success' : 'warning'"
          size="small"
        >
          Embedding {{ status.embedding || "UNKNOWN" }}
        </el-tag>
        <el-tag
          :type="status.chat === 'READY' ? 'success' : 'info'"
          size="small"
          class="ml-2"
        >
          Chat {{ status.chat || "OPTIONAL" }}
        </el-tag>
        <span class="ml-2 muted">引擎 {{ status.engine || "java_rag" }}</span>
        <span class="ml-2 muted">文档 {{ status.localDocCount ?? 0 }}</span>
        <el-tag size="small" type="info" class="ml-2">向量块 {{ status.embeddedChunkCount ?? 0 }}</el-tag>
        <el-tag size="small" type="success" class="ml-2">ready {{ status.readyCount ?? 0 }}</el-tag>
        <el-tag size="small" class="ml-2">local {{ status.localOnlyCount ?? 0 }}</el-tag>
        <div class="hint">{{ status.hint }}</div>
      </div>
    </el-card>

    <div class="grid">
      <!-- 文档列表 -->
      <el-card shadow="never" class="docs-card">
        <template #header>
          <div class="card-header">
            <span>文档库</span>
            <div>
              <el-button size="small" @click="openTextDialog">文本入库</el-button>
              <el-button size="small" type="primary" @click="openUploadDialog">上传文件</el-button>
            </div>
          </div>
        </template>
        <el-table v-loading="loadingDocs" :data="docs" size="small" height="420">
          <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
          <el-table-column prop="domain" label="领域" width="80" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="contentLength" label="字数" width="70" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 问答 -->
      <el-card shadow="never" class="qa-card">
        <template #header>
          <span>知识问答</span>
        </template>
        <div class="quick">
          <el-tag
            v-for="q in quickQuestions"
            :key="q"
            class="q-tag"
            effect="plain"
            round
            @click="applyQuestion(q)"
          >
            {{ q }}
          </el-tag>
        </div>
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          placeholder="例如：7 天无理由退货怎么处理？"
          class="mt-2"
        />
        <div class="actions">
          <el-button type="primary" :loading="querying" @click="handleQuery">检索问答</el-button>
          <span class="muted">向量优先，失败降级关键词</span>
        </div>

        <div v-if="answer" class="answer-box">
          <div class="meta">
            <el-tag size="small" :type="answer.degraded ? 'warning' : 'success'">
              {{ answer.source || "unknown" }}
            </el-tag>
            <el-tag size="small" type="info" class="ml-1">{{ answer.mode }}</el-tag>
            <span v-if="answer.hint" class="muted ml-2">{{ answer.hint }}</span>
          </div>
          <pre class="answer-text">{{ answer.answer }}</pre>
          <div v-if="answer.references?.length" class="refs">
            <div class="refs-title">引用</div>
            <el-collapse>
              <el-collapse-item
                v-for="(ref, idx) in answer.references"
                :key="idx"
                :title="(ref.title || ref.file_path || '引用') + ' #' + (ref.reference_id || idx + 1)"
              >
                <pre class="ref-content">{{ formatRefContent(ref) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 文本入库 -->
    <el-dialog v-model="textDialog" title="文本入库" width="640px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="textForm.title" />
        </el-form-item>
        <el-form-item label="领域">
          <el-select v-model="textForm.domain" style="width: 100%">
            <el-option label="售后" value="售后" />
            <el-option label="运营" value="运营" />
            <el-option label="商品" value="商品" />
            <el-option label="general" value="general" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="textForm.content" type="textarea" :rows="12" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="textDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingText" @click="submitText">入库</el-button>
      </template>
    </el-dialog>

    <!-- 上传 -->
    <el-dialog v-model="uploadDialog" title="上传文件" width="520px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="uploadTitle" placeholder="可选，默认文件名" />
        </el-form-item>
        <el-form-item label="领域">
          <el-select v-model="uploadDomain" style="width: 100%">
            <el-option label="售后" value="售后" />
            <el-option label="运营" value="运营" />
            <el-option label="商品" value="商品" />
            <el-option label="general" value="general" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            v-model:file-list="fileList"
            :auto-upload="false"
            :limit="1"
            drag
          >
            <div class="el-upload__text">拖拽或点击选择 md/txt 等</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-page {
  padding-bottom: 16px;
}
.mb-3 {
  margin-bottom: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-row {
  margin-top: 12px;
}
.hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.ml-1 {
  margin-left: 4px;
}
.ml-2 {
  margin-left: 8px;
}
.mt-2 {
  margin-top: 8px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 12px;
}
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.q-tag {
  cursor: pointer;
}
.actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  align-items: center;
}
.answer-box {
  margin-top: 14px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.answer-text {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 8px 0 0;
  font-family: inherit;
  line-height: 1.55;
}
.refs {
  margin-top: 12px;
}
.refs-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.ref-content {
  white-space: pre-wrap;
  font-size: 13px;
  margin: 0;
}
@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
