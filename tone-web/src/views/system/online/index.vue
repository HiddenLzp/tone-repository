<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <div class="base-toolbar">
      <el-tag type="info" size="small">仅显示在线会话（状态=在线）</el-tag>
      <div class="base-toolbar__spacer" />
      <el-button :icon="Refresh" circle @click="loadData" />
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="username" label="账号" min-width="150">
        <template #default="{ row }">
          <span>{{ row.username }}</span>
          <el-tag v-if="row.current" type="success" size="small" style="margin-left: 6px">
            当前会话
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="登录 IP" min-width="130" />
      <el-table-column prop="location" label="归属地" min-width="140" show-overflow-tooltip />
      <el-table-column prop="browser" label="浏览器" min-width="110" />
      <el-table-column prop="os" label="操作系统" min-width="110" />
      <el-table-column prop="loginTime" label="登录时间" min-width="160" />
      <el-table-column prop="refreshExpireTime" label="会话过期" min-width="160" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPerm="'system:online:kick'"
            link
            type="danger"
            @click="handleKick(row)"
          >
            下线
          </el-button>
          <el-button
            v-hasPerm="'system:online:kick'"
            link
            type="danger"
            @click="handleKickUser(row)"
          >
            全部下线
          </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pageOnline, kickOnline, kickOnlineByUser } from '@/api/system'
import type { OnlineSession } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'

const loading = ref(false)
const records = ref<OnlineSession[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  ip: ''
})

const fields: BaseSearchField[] = [
  { prop: 'username', label: '账号', type: 'input', placeholder: '登录账号', width: 160 },
  { prop: 'ip', label: 'IP', type: 'input', placeholder: '登录 IP', width: 160 }
]

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageOnline({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleKick(row: OnlineSession): Promise<void> {
  await ElMessageBox.confirm(
    `确定下线 ${row.username} 的这一台设备吗？该用户在其它设备上的会话不受影响。`,
    '提示',
    { type: 'warning' }
  )
  await kickOnline(row.id)
  ElMessage.success('已下线该设备')
  loadData()
}

async function handleKickUser(row: OnlineSession): Promise<void> {
  const res = await ElMessageBox.confirm(
    `确定把 ${row.username} 在所有设备上的会话全部下线吗？`,
    '提示',
    { type: 'warning' }
  ).catch(() => null)
  if (!res) return
  await kickOnlineByUser(row.userId)
  ElMessage.success('已下线该用户的全部会话')
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
