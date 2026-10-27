<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button
        v-hasPerm="'job:log:remove'"
        type="danger"
        :icon="Delete"
        :disabled="!selectedIds.length"
        @click="handleBatchDelete"
      >
        批量删除
      </el-button>
      <div class="base-toolbar__spacer" />
      <el-button :icon="Refresh" circle @click="loadData" />
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      border
      stripe
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column prop="jobName" label="任务名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="jobGroup" label="分组" width="100" />
      <el-table-column prop="invokeTarget" label="调用目标" min-width="220" show-overflow-tooltip />
      <el-table-column prop="jobMessage" label="日志信息" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ row.costTime }} ms</template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="160" />
      <el-table-column prop="endTime" label="结束时间" min-width="160" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.exceptionInfo" link type="danger" @click="openDetail(row)">
            异常
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="page__pagination"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <el-drawer v-model="detailVisible" title="异常详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="任务名称">{{ detail.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务分组">{{ detail.jobGroup }}</el-descriptions-item>
        <el-descriptions-item label="调用目标" :span="2">{{ detail.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.endTime }}</el-descriptions-item>
      </el-descriptions>
      <div class="joblog__section">
        <div class="joblog__label">日志信息</div>
        <pre class="joblog__code">{{ detail.jobMessage || '无' }}</pre>
      </div>
      <div class="joblog__section">
        <div class="joblog__label">异常堆栈</div>
        <pre class="joblog__code joblog__code--error">{{ detail.exceptionInfo }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { pageJobLogs, deleteJobLogs } from '@/api/job'
import type { SysJobLog } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_RESULT_OPTIONS } from '@/components/BaseSearch/types'

const loading = ref(false)
const records = ref<SysJobLog[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  jobName: '',
  jobGroup: '',
  status: undefined as number | undefined
})

const fields: BaseSearchField[] = [
  { prop: 'jobName', label: '任务名称', type: 'input', width: 160 },
  { prop: 'jobGroup', label: '任务分组', type: 'input', placeholder: '如 DEFAULT', width: 130 },
  { prop: 'status', label: '状态', type: 'select', width: 100, options: STATUS_RESULT_OPTIONS }
]

const detailVisible = ref(false)
const detail = ref<SysJobLog>({ id: 0 })

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageJobLogs({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: SysJobLog[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

function openDetail(row: SysJobLog): void {
  detail.value = row
  detailVisible.value = true
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 条任务日志吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteJobLogs(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  loadData()
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

.joblog {
  &__section {
    margin-top: 14px;
  }

  &__label {
    margin-bottom: 6px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__code {
    max-height: 320px;
    margin: 0;
    padding: 10px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    background: var(--el-fill-color-light);
    border-radius: 4px;

    &--error {
      color: var(--el-color-danger);
    }
  }
}
</style>
