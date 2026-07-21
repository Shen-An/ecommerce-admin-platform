<!-- AI 模型配置：Chat LLM + Embedding + LightRAG -->
<script setup lang="ts">
defineOptions({
  name: "AiSettings",
  inheritAttrs: false,
});

import {
  getAiModelConfig,
  saveAiModelConfig,
  testAiConnection,
} from "@/api/ai/settings";
import {
  AiModelConfigForm,
  AiModelConfigVO,
  ModelPreset,
  ProviderOption,
} from "@/api/ai/settings/types";

const formRef = ref(ElForm);
const loading = ref(false);
const saving = ref(false);
const testing = ref<Record<string, boolean>>({
  chat: false,
  embedding: false,
  lightrag: false,
});

const meta = reactive({
  chatApiKeyMasked: "",
  chatApiKeyConfigured: false,
  embeddingApiKeyMasked: "",
  embeddingApiKeyConfigured: false,
  chatProviders: [] as ProviderOption[],
  embeddingProviders: [] as ProviderOption[],
  chatPresets: [] as ModelPreset[],
  embeddingPresets: [] as ModelPreset[],
});

const formData = reactive<AiModelConfigForm>({
  configKey: "default",
  chatProvider: "dashscope",
  chatBaseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
  chatApiKey: "",
  chatModel: "qwen-plus",
  chatTemperature: 0.7,
  embeddingProvider: "nvidia",
  embeddingBaseUrl: "https://integrate.api.nvidia.com/v1",
  embeddingApiKey: "",
  embeddingModel: "nvidia/llama-nemotron-embed-1b-v2",
  embeddingDim: 2048,
  lightragBaseUrl: "http://localhost:9621",
  mockEnabled: 1,
});

const rules = {
  chatProvider: [{ required: true, message: "请选择对话提供商", trigger: "change" }],
  chatModel: [{ required: true, message: "请输入对话模型名", trigger: "blur" }],
  embeddingProvider: [
    { required: true, message: "请选择 Embedding 提供商", trigger: "change" },
  ],
  embeddingModel: [
    { required: true, message: "请输入 Embedding 模型名", trigger: "blur" },
  ],
};

function applyVo(data: AiModelConfigVO) {
  formData.configKey = data.configKey || "default";
  formData.chatProvider = data.chatProvider || "dashscope";
  formData.chatBaseUrl = data.chatBaseUrl || "";
  formData.chatApiKey = "";
  formData.chatModel = data.chatModel || "qwen-plus";
  formData.chatTemperature = Number(data.chatTemperature ?? 0.7);
  formData.embeddingProvider = data.embeddingProvider || "nvidia";
  formData.embeddingBaseUrl = data.embeddingBaseUrl || "";
  formData.embeddingApiKey = "";
  formData.embeddingModel =
    data.embeddingModel || "nvidia/llama-nemotron-embed-1b-v2";
  formData.embeddingDim = data.embeddingDim ?? 2048;
  formData.lightragBaseUrl = data.lightragBaseUrl || "http://localhost:9621";
  formData.mockEnabled = data.mockEnabled ?? 1;

  meta.chatApiKeyMasked = data.chatApiKeyMasked || "";
  meta.chatApiKeyConfigured = !!data.chatApiKeyConfigured;
  meta.embeddingApiKeyMasked = data.embeddingApiKeyMasked || "";
  meta.embeddingApiKeyConfigured = !!data.embeddingApiKeyConfigured;
  meta.chatProviders = data.chatProviders || [];
  meta.embeddingProviders = data.embeddingProviders || [];
  meta.chatPresets = data.chatPresets || [];
  meta.embeddingPresets = data.embeddingPresets || [];
}

function loadConfig() {
  loading.value = true;
  getAiModelConfig("default")
    .then(({ data }) => applyVo(data))
    .finally(() => {
      loading.value = false;
    });
}

function onChatProviderChange(val: string) {
  const p = meta.chatProviders.find((x) => x.value === val);
  if (p?.defaultBaseUrl) {
    formData.chatBaseUrl = p.defaultBaseUrl;
  }
}

function onEmbeddingProviderChange(val: string) {
  const p = meta.embeddingProviders.find((x) => x.value === val);
  if (p?.defaultBaseUrl) {
    formData.embeddingBaseUrl = p.defaultBaseUrl;
  }
}

function applyChatPreset(preset: ModelPreset) {
  formData.chatProvider = preset.provider;
  formData.chatModel = preset.model;
  if (preset.baseUrl) {
    formData.chatBaseUrl = preset.baseUrl;
  }
}

function applyEmbeddingPreset(preset: ModelPreset) {
  formData.embeddingProvider = preset.provider;
  formData.embeddingModel = preset.model;
  if (preset.baseUrl) {
    formData.embeddingBaseUrl = preset.baseUrl;
  }
  if (preset.embeddingDim) {
    formData.embeddingDim = preset.embeddingDim;
  }
}

function handleSave() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    saving.value = true;
    const payload: AiModelConfigForm = { ...formData };
    // 空密钥不提交，后端保留原值
    if (!payload.chatApiKey) {
      delete (payload as any).chatApiKey;
    }
    if (!payload.embeddingApiKey) {
      delete (payload as any).embeddingApiKey;
    }
    saveAiModelConfig(payload)
      .then(() => {
        ElMessage.success("模型配置已保存");
        loadConfig();
      })
      .finally(() => {
        saving.value = false;
      });
  });
}

function handleTest(type: "chat" | "embedding" | "lightrag") {
  testing.value[type] = true;
  const payload: Partial<AiModelConfigForm> = {
    chatProvider: formData.chatProvider,
    chatBaseUrl: formData.chatBaseUrl,
    chatApiKey: formData.chatApiKey || undefined,
    chatModel: formData.chatModel,
    embeddingProvider: formData.embeddingProvider,
    embeddingBaseUrl: formData.embeddingBaseUrl,
    embeddingApiKey: formData.embeddingApiKey || undefined,
    embeddingModel: formData.embeddingModel,
    lightragBaseUrl: formData.lightragBaseUrl,
  };
  testAiConnection(type, payload)
    .then(({ data }) => {
      if (data?.success) {
        const extra =
          type === "embedding" && data.detectedDim
            ? `，检测到维度 ${data.detectedDim}`
            : "";
        ElMessage.success((data.message || "连通成功") + extra);
        if (type === "embedding" && data.detectedDim) {
          formData.embeddingDim = data.detectedDim;
        }
      } else {
        ElMessage.error(data?.message || "连通失败");
      }
    })
    .catch(() => {
      /* interceptor 已提示 */
    })
    .finally(() => {
      testing.value[type] = false;
    });
}

onMounted(() => {
  loadConfig();
});
</script>

<template>
  <div class="app-container" v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
      title="在此配置运营助手对话模型与知识库 Embedding。当前默认 Embedding：nvidia/llama-nemotron-embed-1b-v2（NVIDIA）。API Key 仅在填写时更新，留空保留已有密钥。"
    />

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="140px"
      class="ai-settings-form"
    >
      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>对话模型（Chat LLM）</span>
            <el-button
              type="primary"
              link
              :loading="testing.chat"
              @click="handleTest('chat')"
            >
              测试连通
            </el-button>
          </div>
        </template>

        <el-form-item label="快捷预设">
          <el-space wrap>
            <el-tag
              v-for="p in meta.chatPresets"
              :key="p.provider + p.model"
              class="preset-tag"
              effect="plain"
              @click="applyChatPreset(p)"
            >
              {{ p.label }}
            </el-tag>
          </el-space>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :md="12" :sm="24">
            <el-form-item label="提供商" prop="chatProvider">
              <el-select
                v-model="formData.chatProvider"
                class="w-full"
                @change="onChatProviderChange"
              >
                <el-option
                  v-for="p in meta.chatProviders"
                  :key="p.value"
                  :label="p.label"
                  :value="p.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :sm="24">
            <el-form-item label="模型名" prop="chatModel">
              <el-input v-model="formData.chatModel" placeholder="如 qwen-plus" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="Base URL">
          <el-input
            v-model="formData.chatBaseUrl"
            placeholder="OpenAI 兼容接口根路径，如 https://dashscope.aliyuncs.com/compatible-mode/v1"
          />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="formData.chatApiKey"
            type="password"
            show-password
            clearable
            :placeholder="
              meta.chatApiKeyConfigured
                ? `已配置 ${meta.chatApiKeyMasked}（留空不修改）`
                : '请输入对话 API Key'
            "
          />
        </el-form-item>

        <el-form-item label="Temperature">
          <el-slider
            v-model="formData.chatTemperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-input
            style="max-width: 420px"
          />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>Embedding 模型（LightRAG 向量化）</span>
            <el-button
              type="primary"
              link
              :loading="testing.embedding"
              @click="handleTest('embedding')"
            >
              测试连通
            </el-button>
          </div>
        </template>

        <el-form-item label="快捷预设">
          <el-space wrap>
            <el-tag
              v-for="p in meta.embeddingPresets"
              :key="p.provider + p.model"
              class="preset-tag"
              :type="
                p.model === 'nvidia/llama-nemotron-embed-1b-v2'
                  ? 'success'
                  : 'info'
              "
              effect="plain"
              @click="applyEmbeddingPreset(p)"
            >
              {{ p.label }}
            </el-tag>
          </el-space>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :md="12" :sm="24">
            <el-form-item label="提供商" prop="embeddingProvider">
              <el-select
                v-model="formData.embeddingProvider"
                class="w-full"
                @change="onEmbeddingProviderChange"
              >
                <el-option
                  v-for="p in meta.embeddingProviders"
                  :key="p.value"
                  :label="p.label"
                  :value="p.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :sm="24">
            <el-form-item label="模型名" prop="embeddingModel">
              <el-input
                v-model="formData.embeddingModel"
                placeholder="nvidia/llama-nemotron-embed-1b-v2"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="Base URL">
          <el-input
            v-model="formData.embeddingBaseUrl"
            placeholder="https://integrate.api.nvidia.com/v1"
          />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="formData.embeddingApiKey"
            type="password"
            show-password
            clearable
            :placeholder="
              meta.embeddingApiKeyConfigured
                ? `已配置 ${meta.embeddingApiKeyMasked}（留空不修改）`
                : '请输入 Embedding API Key（NVIDIA NGC / NIM）'
            "
          />
        </el-form-item>

        <el-form-item label="向量维度">
          <el-input-number
            v-model="formData.embeddingDim"
            :min="64"
            :max="8192"
            :step="64"
          />
          <span class="form-tip">
            llama-nemotron-embed-1b-v2 通常为 2048；保存后可「测试连通」自动探测
          </span>
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>LightRAG 与运行策略</span>
            <el-button
              type="primary"
              link
              :loading="testing.lightrag"
              @click="handleTest('lightrag')"
            >
              测试可达
            </el-button>
          </div>
        </template>

        <el-form-item label="LightRAG 地址">
          <el-input
            v-model="formData.lightragBaseUrl"
            placeholder="http://localhost:9621"
          />
        </el-form-item>

        <el-form-item label="Mock 降级">
          <el-switch
            v-model="formData.mockEnabled"
            :active-value="1"
            :inactive-value="0"
            active-text="开启（无 Key / 故障时规则回复）"
            inactive-text="关闭"
          />
        </el-form-item>
      </el-card>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存配置
        </el-button>
        <el-button @click="loadConfig">重新加载</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.ai-settings-form {
  max-width: 960px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.preset-tag {
  cursor: pointer;
}
.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mb-4 {
  margin-bottom: 16px;
}
.w-full {
  width: 100%;
}
</style>
