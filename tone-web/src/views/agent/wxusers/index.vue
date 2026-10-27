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
      <el-table-column prop="openId" label="用户 openId" min-width="180" show-overflow-tooltip />
      <el-table-column prop="nickname" label="昵称" width="110">
        <template #default="{ row }">{{ row.nickname || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastIp" label="最近访问 IP" width="140">
        <template #default="{ row }">{{ row.lastIp || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastLocation" label="归属地" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastLocation || '-' }}</template>
      </el-table-column>
      <el-table-column label="设备/微信版本" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <!-- UA 很长，列表里只提示有无，全文在详情抽屉 -->
          <span v-if="row.lastUa">{{ shortUa(row.lastUa) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="visitCount" label="访问次数" width="90" />
      <el-table-column prop="lastActiveAt" label="最近活跃" min-width="160" />
      <el-table-column prop="createTime" label="注册时间" min-width="160" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'agent:wxuser:query'" link type="primary" @click="openDetail(row.id)">
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
    <el-drawer v-model="detailVisible" title="微信用户详情" size="560px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="openId">{{ detail.openId }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detail.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近访问 IP">{{ detail.lastIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归属地">{{ detail.lastLocation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="访问次数">{{ detail.visitCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="最近活跃">{{ detail.lastActiveAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ detail.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="wxusers__section">
        <div class="wxusers__label">User-Agent（设备/微信版本）</div>
        <pre class="wxusers__code">{{ detail?.lastUa || '无' }}</pre>
      </div>
      <div class="wxusers__hint">
        个人主体小程序拿不到微信昵称头像接口，昵称/头像仅在用户主动授权时才有值；
        访问信息（IP/归属地/UA/次数）由后端在每次 API 请求时节流采集（同一用户 5 分钟一次）。
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { pageWxUsers, getWxUser } from '@/api/agent'
import type { WxUserRow, WxUserQuery } from '@/api/agent'

const loading = ref(false)
const records = ref<WxUserRow[]>([])
const total = ref(0)

const query = reactive<WxUserQuery>({
  pageNum: 1,
  pageSize: 10,
  openId: ''
})

const fields: BaseSearchField[] = [
  { prop: 'openId', label: 'openId', type: 'input', placeholder: '用户 openId', width: 220 }
]

const detailVisible = ref(false)
const detail = ref<WxUserRow | null>(null)

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageWxUsers({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number): Promise<void> {
  detail.value = await getWxUser(id)
  detailVisible.value = true
}

/**
 * UA 精简展示：微信 UA 是一长串浏览器内核信息，对运营有用的其实是
 * 末尾的 MicroMessenger 版本和括号里的平台描述，取关键词拼出来
 */
function shortUa(ua: string): string {
  const mm = ua.match(/MicroMessenger\/[\d.]+/)
  const platform = /Windows/i.test(ua)
    ? 'Windows'
    : /iPhone|iPad|iOS/i.test(ua)
      ? 'iOS'
      : /Android/i.test(ua)
        ? 'Android'
        : /devtools/i.test(ua)
          ? '开发者工具'
          : '未知平台'
  return [platform, mm?.[0]].filter(Boolean).join(' / ')
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

.wxusers {
  &__section {
    margin-top: 14px;
  }

  &__label {
    margin-bottom: 6px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__code {
    max-height: 160px;
    margin: 0;
    padding: 10px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }

  &__hint {
    margin-top: 14px;
    font-size: 12px;
    line-height: 1.7;
    color: var(--el-text-color-secondary);
  }
}
</style>
