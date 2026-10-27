<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button
        v-hasPerm="'monitor:oplog:remove'"
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
      <el-table-column prop="title" label="模块标题" min-width="120" />
      <el-table-column label="业务类型" width="90">
        <template #default="{ row }">
          <el-tag :type="businessTagType(row.businessType)" size="small">
            {{ businessText(row.businessType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operName" label="操作人员" width="110" />
      <el-table-column label="被操作对象" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-if="targetUser(row)" type="danger" size="small">{{ targetUser(row) }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="operIp" label="操作 IP" width="130" />
      <el-table-column prop="operLocation" label="归属地" min-width="120" show-overflow-tooltip />
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
      <el-table-column prop="createTime" label="操作时间" min-width="160" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
    <el-drawer v-model="detailVisible" title="操作日志详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="模块标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ businessText(detail.businessType) }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人员">{{ detail.operName }}</el-descriptions-item>
        <el-descriptions-item label="操作地址">{{ detail.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="操作方法">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="操作 IP">{{ detail.operIp }}</el-descriptions-item>
        <el-descriptions-item label="归属地">{{ detail.operLocation }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 0 ? 'success' : 'danger'" size="small">
            {{ detail.status === 0 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="oplog__section">
        <div class="oplog__label">请求参数</div>
        <pre class="oplog__code">{{ detail.operParam || '无' }}</pre>
      </div>
      <div class="oplog__section">
        <div class="oplog__label">返回结果</div>
        <pre class="oplog__code">{{ detail.jsonResult || '无' }}</pre>
      </div>
      <div v-if="detail.errorMsg" class="oplog__section">
        <div class="oplog__label">异常信息</div>
        <pre class="oplog__code oplog__code--error">{{ detail.errorMsg }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { pageOperationLogs, deleteOperationLogs } from '@/api/monitor'
import type { OperationLog, OperationLogQuery } from '@/types/api'

/** 与后端 BusinessType 枚举一一对应 */
import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_RESULT_OPTIONS } from '@/components/BaseSearch/types'

const businessTypes = [
  { value: 0, label: '其它' },
  { value: 1, label: '新增' },
  { value: 2, label: '修改' },
  { value: 3, label: '删除' },
  { value: 4, label: '导出' },
  { value: 5, label: '导入' },
  { value: 6, label: '授权' },
  { value: 7, label: '强退' }
]

const loading = ref(false)
const records = ref<OperationLog[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive<OperationLogQuery>({
  pageNum: 1,
  pageSize: 10,
  title: '',
  operName: '',
  businessType: undefined,
  status: undefined,
  beginTime: undefined,
  endTime: undefined
})

const fields: BaseSearchField[] = [
  { prop: 'title', label: '模块标题', type: 'input', placeholder: '如：用户管理', width: 150 },
  { prop: 'operName', label: '操作人员', type: 'input', placeholder: '登录账号', width: 140 },
  {
    prop: 'businessType',
    label: '业务类型',
    type: 'select',
    width: 110,
    options: businessTypes.map((b) => ({ label: b.label, value: b.value }))
  },
  { prop: 'status', label: '状态', type: 'select', width: 100, options: STATUS_RESULT_OPTIONS },
  {
    label: '操作时间',
    type: 'datetimerange',
    beginKey: 'beginTime',
    endKey: 'endTime',
    width: 340
  }
]

const detailVisible = ref(false)
const detail = ref<OperationLog>({ id: 0 })

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageOperationLogs({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: OperationLog[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

function openDetail(row: OperationLog): void {
  detail.value = row
  detailVisible.value = true
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 条操作日志吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteOperationLogs(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  loadData()
}

/**
 * 被操作对象：强退类日志的操作日志里，Service 会把「踢的是谁」写进 operParam
 * （{"args":12,"targetUser":"zhangsan","targetIp":"192.168.10.5"}）。
 * 列表里直接显示出来——不然想知道踢了谁，只能一条条点开详情翻 JSON。
 */
function targetUser(row: OperationLog): string {
  if (row.businessType !== 7 || !row.operParam) {
    return ''
  }
  try {
    const parsed = JSON.parse(row.operParam) as { targetUser?: unknown }
    return typeof parsed.targetUser === 'string' ? parsed.targetUser : ''
  } catch {
    return ''
  }
}

function businessText(type?: number): string {
  return businessTypes.find((b) => b.value === type)?.label ?? '其它'
}

function businessTagType(type?: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (type) {
    case 1:
      return 'success'
    case 2:
      return 'primary'
    case 3:
      return 'danger'
    case 6:
    case 7:
      return 'warning'
    default:
      return 'info'
  }
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

.oplog {
  &__section {
    margin-top: 14px;
  }

  &__label {
    margin-bottom: 6px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__code {
    max-height: 240px;
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
