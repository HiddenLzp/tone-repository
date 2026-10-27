<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button
        v-hasPerm="'monitor:loginlog:remove'"
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
      <el-table-column prop="username" label="登录账号" min-width="120" />
      <el-table-column prop="ip" label="登录 IP" width="130" />
      <el-table-column prop="location" label="归属地" min-width="140" show-overflow-tooltip />
      <el-table-column prop="browser" label="浏览器" min-width="110" />
      <el-table-column prop="os" label="操作系统" min-width="110" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="msg" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column prop="loginTime" label="登录时间" min-width="160" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { pageLoginLogs, deleteLoginLogs } from '@/api/monitor'
import type { LoginLog, LoginLogQuery } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_RESULT_OPTIONS } from '@/components/BaseSearch/types'

const loading = ref(false)
const records = ref<LoginLog[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive<LoginLogQuery>({
  pageNum: 1,
  pageSize: 10,
  username: '',
  ip: '',
  status: undefined,
  beginTime: undefined,
  endTime: undefined
})

const fields: BaseSearchField[] = [
  { prop: 'username', label: '登录账号', type: 'input', width: 150 },
  { prop: 'ip', label: '登录 IP', type: 'input', width: 150 },
  { prop: 'status', label: '状态', type: 'select', width: 100, options: STATUS_RESULT_OPTIONS },
  {
    label: '登录时间',
    type: 'datetimerange',
    beginKey: 'beginTime',
    endKey: 'endTime',
    width: 340
  }
]

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageLoginLogs({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: LoginLog[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 条登录日志吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteLoginLogs(selectedIds.value.join(','))
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
</style>
