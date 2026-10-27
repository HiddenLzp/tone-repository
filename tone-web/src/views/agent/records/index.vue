<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <div class="base-toolbar__spacer" />
      <el-button :icon="Refresh" circle @click="loadData" />
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="openId" label="用户 openId" min-width="170" show-overflow-tooltip />
      <el-table-column label="场景" width="90">
        <template #default="{ row }">
          <el-tag size="small">{{ sceneText(row.scene) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="originalText" label="原文" min-width="180" show-overflow-tooltip />
      <el-table-column label="礼貌度" width="90">
        <template #default="{ row }">{{ row.scores?.politeness ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="专业度" width="90">
        <template #default="{ row }">{{ row.scores?.professionalism ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="情感" width="80">
        <template #default="{ row }">{{ row.scores?.emotion ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="轮数" width="70">
        <template #default="{ row }">{{ row.rounds }}</template>
      </el-table-column>
      <el-table-column label="Token" width="110">
        <template #default="{ row }">{{ (row.promptTokens ?? 0) + (row.completionTokens ?? 0) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" min-width="160" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'agent:records:query'" link type="primary" @click="openDetail(row.id)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="page__pagination"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="检查记录详情" size="640px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.record.id }}</el-descriptions-item>
        <el-descriptions-item label="场景">{{ sceneText(detail.record.scene) }}</el-descriptions-item>
        <el-descriptions-item label="用户 openId" :span="2">{{ detail.record.openId }}</el-descriptions-item>
        <el-descriptions-item label="时间" :span="2">{{ detail.record.createTime }}</el-descriptions-item>
        <el-descriptions-item label="礼貌度">{{ detail.record.scores?.politeness ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="专业度">{{ detail.record.scores?.professionalism ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="情感倾向">{{ detail.record.scores?.emotion ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Agent 轮数">{{ detail.record.rounds }}</el-descriptions-item>
        <el-descriptions-item label="Token 消耗" :span="2">
          prompt {{ detail.record.promptTokens ?? 0 }} / completion {{ detail.record.completionTokens ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="原文" :span="2">{{ detail.record.originalText }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.record.finalText" label="最终采用文本" :span="2">
          {{ detail.record.finalText }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="records__section">
        <div class="records__label">问题列表</div>
        <el-empty v-if="!detail?.record.issues?.length" description="无" :image-size="48" />
        <div v-for="(issue, i) in detail?.record.issues ?? []" :key="i" class="records__issue">
          <el-tag :type="levelTag(issue.level)" size="small">{{ levelText(issue.level) }}</el-tag>
          <span class="records__issue-quote">「{{ issue.quote }}」</span>
          <span class="records__issue-suggestion">{{ issue.suggestion }}</span>
        </div>
      </div>

      <div class="records__section">
        <div class="records__label">改写版本</div>
        <el-empty v-if="!detail?.record.versions?.length" description="无（未完成或被拦截）" :image-size="48" />
        <div v-for="(v, i) in detail?.record.versions ?? []" :key="i" class="records__version">
          <el-tag type="primary" size="small">{{ v.style }}</el-tag>
          <div class="records__version-text">{{ v.text }}</div>
        </div>
      </div>

      <div class="records__section">
        <div class="records__label">对话明细</div>
        <el-empty v-if="!detail?.messages.length" description="无" :image-size="48" />
        <div v-for="m in structuredMessages" :key="m.key" class="records__msg">
          <div class="records__msg-head">
            <el-tag size="small" :type="m.phaseTag">{{ m.phase }}</el-tag>
            <el-tag size="small" :type="m.role === 'user' ? 'info' : 'primary'">
              {{ m.role === 'user' ? '用户' : 'Agent' }}
            </el-tag>
          </div>
          <!-- 首轮 Agent 消息存的是 versions JSON，结构化成版本列表 -->
          <template v-if="m.versions">
            <div v-for="(v, vi) in m.versions" :key="vi" class="records__msg-version">
              <el-tag size="small" effect="plain">{{ v.style }}</el-tag>
              <div class="records__msg-text">{{ v.text }}</div>
            </div>
          </template>
          <div v-else class="records__msg-text">{{ m.text }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { pageRecords, getRecordDetail } from '@/api/agent'
import type { ToneRecord, ToneRecordQuery, RecordDetail, RecordMessage } from '@/api/agent'

const sceneOptions = [
  { label: '职场', value: 'workplace' },
  { label: '社交', value: 'social' },
  { label: '亲密关系', value: 'relationship' },
  { label: '朋友圈', value: 'moments' },
  { label: '客服', value: 'service' }
]

const statusOptions = [
  { label: '完成', value: 'done' },
  { label: '敏感词拦截', value: 'blocked' },
  { label: '异常', value: 'error' }
]

const loading = ref(false)
const records = ref<ToneRecord[]>([])
const total = ref(0)

const query = reactive<ToneRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  scene: undefined,
  openId: '',
  status: undefined,
  beginTime: undefined,
  endTime: undefined
})

const fields: BaseSearchField[] = [
  { prop: 'openId', label: 'openId', type: 'input', placeholder: '用户 openId', width: 180 },
  { prop: 'scene', label: '场景', type: 'select', width: 120, options: sceneOptions },
  { prop: 'status', label: '状态', type: 'select', width: 120, options: statusOptions },
  {
    label: '时间范围',
    type: 'datetimerange',
    beginKey: 'beginTime',
    endKey: 'endTime',
    width: 340
  }
]

const detailVisible = ref(false)
const detail = ref<RecordDetail | null>(null)

interface StructuredMessage {
  key: number
  phase: string
  phaseTag: 'primary' | 'warning' | 'info'
  role: string
  text: string
  versions?: Array<{ style: string; text: string }>
}

/** 对话明细结构化：首轮 assistant 存的是 versions JSON，微调/回退是纯文本 */
const structuredMessages = computed<StructuredMessage[]>(() => {
  const messages: RecordMessage[] = detail.value?.messages ?? []
  return messages.map((m, i) => {
    const isRestore = m.role === 'user' && m.content?.includes('采用历史版本')
    const phase = m.refineFlag === 1 ? (isRestore ? '回退' : '微调') : '首轮'
    const base: StructuredMessage = {
      key: m.id ?? i,
      phase,
      phaseTag: phase === '首轮' ? 'primary' : phase === '微调' ? 'warning' : 'info',
      role: m.role,
      text: m.content
    }
    if (m.role === 'assistant' && m.refineFlag === 0) {
      try {
        const parsed = JSON.parse(m.content) as Array<{ style: string; text: string }>
        if (Array.isArray(parsed)) {
          base.versions = parsed
          base.text = ''
        }
      } catch {
        // 非 JSON 内容按纯文本展示
      }
    }
    return base
  })
})

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageRecords({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number): Promise<void> {
  detail.value = await getRecordDetail(id)
  detailVisible.value = true
}

function sceneText(scene: string): string {
  return sceneOptions.find((s) => s.value === scene)?.label ?? scene
}

function statusText(status: string): string {
  return statusOptions.find((s) => s.value === status)?.label ?? status
}

function statusTag(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'done') {
    return 'success'
  }
  if (status === 'blocked') {
    return 'danger'
  }
  return 'info'
}

function levelText(level: string): string {
  return level === 'high' ? '严重' : level === 'mid' ? '中等' : '轻微'
}

function levelTag(level: string): 'danger' | 'warning' | 'info' {
  return level === 'high' ? 'danger' : level === 'mid' ? 'warning' : 'info'
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page {
  &__pagination {
    margin-top: 12px;
    justify-content: flex-end;
  }
}

.records {
  &__section {
    margin-top: 16px;
  }

  &__label {
    margin-bottom: 8px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__issue {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    padding: 6px 0;
    font-size: 13px;
    line-height: 1.6;

    &-quote {
      color: var(--el-color-danger);
      word-break: break-all;
    }

    &-suggestion {
      color: var(--el-text-color-regular);
      word-break: break-all;
    }
  }

  &__version {
    padding: 8px 0;

    &-text {
      margin-top: 4px;
      font-size: 13px;
      line-height: 1.7;
      color: var(--el-text-color-regular);
    }
  }

  &__msg {
    padding: 10px 12px;
    margin-bottom: 10px;
    background: var(--el-fill-color-light);
    border-radius: 6px;

    &-head {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
    }

    &-version {
      margin-bottom: 6px;
    }

    &-text {
      font-size: 13px;
      line-height: 1.7;
      white-space: pre-wrap;
      word-break: break-all;
      color: var(--el-text-color-regular);
    }
  }
}
</style>
