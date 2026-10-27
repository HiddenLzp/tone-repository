<template>
  <div class="page">
    <!-- LLM / 配额 / 告警：保存即生效（sys_config 30 分钟缓存被写清除） -->
    <el-card shadow="never">
      <template #header>运行配置（保存后立即生效，无需重启服务）</template>
      <el-form :model="form" label-width="150px" style="max-width: 560px" v-loading="loading">
        <el-divider content-position="left">模型</el-divider>
        <el-form-item label="主模型">
          <el-input v-model="form.model" placeholder="如 qwen3.8-max" />
          <div class="form-hint">每次检查/微调实际使用的模型（含 Agent 工具内部调用）</div>
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="备用模型（降级）">
          <el-input v-model="form.fallbackModel" placeholder="留空 = 用启动配置" />
          <div class="form-hint">
            仅当服务启动时配置了 tone.llm.fallback.model 才会启用降级；这里改的是降级时用的模型名
          </div>
        </el-form-item>

        <el-divider content-position="left">配额</el-divider>
        <el-form-item label="每日免费次数">
          <el-input-number v-model="form.dailyFreeCount" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="每日奖励上限">
          <el-input-number v-model="form.dailyRewardMax" :min="0" :max="1000" />
          <div class="form-hint">看激励视频每日最多可获得的额外次数</div>
        </el-form-item>

        <el-divider content-position="left">成本告警</el-divider>
        <el-form-item label="Token 日告警阈值">
          <el-input-number v-model="form.tokenAlertThreshold" :min="0" :step="10000" />
          <div class="form-hint">当日 Token 总消耗达到阈值时看板顶部亮红横幅，且每日 21:30 定时任务打告警日志；0 = 关闭</div>
        </el-form-item>

        <el-form-item v-hasPerm="'agent:settings:edit'">
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 敏感词库 -->
    <el-card shadow="never" class="words-card">
      <template #header>
        <span>敏感词库（每行一个词，支持 # 注释；保存即生效）</span>
        <el-tag v-if="external" size="small" type="warning" class="words-tag">外部文件</el-tag>
        <el-tag v-else size="small" type="info" class="words-tag">默认词库（未保存过）</el-tag>
      </template>
      <el-input
        v-model="wordsText"
        type="textarea"
        :rows="10"
        placeholder="每行一个敏感词"
        spellcheck="false"
      />
      <div class="words-actions">
        <el-button v-hasPerm="'agent:settings:edit'" type="primary" :loading="savingWords" @click="saveWords">
          保存词库
        </el-button>
        <span class="form-hint">保存后写入服务器 config/sensitive-words.txt（重启不丢），检查链路立即使用新词库</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAgentSettings, saveAgentSettings, getSensitiveWords, saveSensitiveWords } from '@/api/agent'
import type { AgentSettings } from '@/api/agent'

const loading = ref(false)
const saving = ref(false)
const savingWords = ref(false)
const external = ref(false)
const wordsText = ref('')

const form = reactive<AgentSettings>({
  model: '',
  temperature: 0.7,
  fallbackModel: '',
  dailyFreeCount: 3,
  dailyRewardMax: 10,
  tokenAlertThreshold: 0
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const s = await getAgentSettings()
    Object.assign(form, s)
    const w = await getSensitiveWords()
    wordsText.value = w.words.join('\n')
    external.value = w.external
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const saved = await saveAgentSettings({ ...form })
    Object.assign(form, saved)
    ElMessage.success('已保存并生效')
  } finally {
    saving.value = false
  }
}

async function saveWords(): Promise<void> {
  savingWords.value = true
  try {
    const words = wordsText.value
      .split('\n')
      .map((w) => w.trim())
      .filter((w) => w !== '')
    const saved = await saveSensitiveWords(words)
    wordsText.value = saved.words.join('\n')
    external.value = saved.external
    ElMessage.success(`词库已保存（${saved.words.length} 个词）并即时生效`)
  } finally {
    savingWords.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.page {
  .words-card {
    margin-top: 12px;
  }

  .words-tag {
    margin-left: 8px;
  }

  .words-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 10px;
  }
}

.form-hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
