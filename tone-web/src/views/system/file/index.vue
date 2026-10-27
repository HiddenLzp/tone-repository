<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-upload
        v-hasPerm="'system:file:upload'"
        :http-request="customUpload"
        :show-file-list="false"
        multiple
      >
        <el-button type="primary" :icon="UploadFilled">上传文件</el-button>
      </el-upload>
      <el-button
        v-hasPerm="'system:file:remove'"
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
      <el-table-column label="预览" width="80" align="center">
        <template #default="{ row }">
          <!--
            缩略图读展示件（压缩图），放大后看的是原图：
            fileUrl 与 originUrl 是两个互不相干的随机文件名 + 不同目录，
            知道压缩图地址也推不出原图，爬虫拿不到不带签名的那一份
          -->
          <el-image
            v-if="isImage(row)"
            :src="fileUrl(row)"
            :preview-src-list="[originUrl(row)]"
            fit="cover"
            preview-teleported
            class="file__thumb"
          />
          <el-icon v-else :size="22" color="#909399"><Document /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="大小" width="140">
        <template #default="{ row }">
          <div>{{ formatSize(row.fileSize) }}</div>
          <div v-if="row.compressed === 1" class="file__origin-size">
            原图 {{ formatSize(row.originSize ?? row.fileSize) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ext" label="类型" width="80" />
      <el-table-column prop="bizType" label="业务类型" width="110" />
      <el-table-column label="访问地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :href="fileUrl(row)" target="_blank">{{ row.fileUrl }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="上传时间" min-width="160" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="copyUrl(fileUrl(row))">复制链接</el-button>
          <el-button
            v-if="isImage(row)"
            link
            type="primary"
            @click="viewOrigin(row)"
          >
            查看原图
          </el-button>
          <el-button
            v-hasPerm="'system:file:remove'"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
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
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete, UploadFilled, Document } from '@element-plus/icons-vue'
import { pageFiles, uploadFile, deleteFiles } from '@/api/system'
import type { SysFile } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'

const loading = ref(false)
const records = ref<SysFile[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  originalName: '',
  bizType: ''
})

const fields: BaseSearchField[] = [
  { prop: 'originalName', label: '文件名', type: 'input', placeholder: '原始文件名', width: 180 },
  { prop: 'bizType', label: '业务类型', type: 'input', placeholder: '如 avatar', width: 140 }
]

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageFiles({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: SysFile[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

/**
 * 自定义上传：走统一的 request 实例，才能带上 Authorization 头。
 * el-upload 自带的 action 是独立 xhr，拿不到我们拦截器里的 token。
 */
async function customUpload(options: UploadRequestOptions): Promise<void> {
  try {
    await uploadFile(options.file)
    ElMessage.success('上传成功')
    loadData()
    options.onSuccess({})
  } catch {
    // 失败只提示，不回调 onError：本组件设了 show-file-list=false，
    // 没有文件条目要置为失败态，回调了反而会在控制台留一条未处理的 UploadAjaxError
    ElMessage.error('上传失败')
  }
}

async function handleDelete(row: SysFile): Promise<void> {
  await ElMessageBox.confirm(`确定删除文件 ${row.originalName} 吗？`, '提示', { type: 'warning' })
  await deleteFiles(String(row.id))
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 个文件吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteFiles(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  loadData()
}

/**
 * 带签名的访问地址统一补网关前缀。
 *
 * 后端返回的路径有两种形态：
 *  - /upload/...        静态资源，Nginx / vite 已经直接转发，不需要前缀；
 *  - /system/file/...   后端接口，必须带上 VITE_API_PREFIX（开发环境是 vite 代理 /api）。
 * 签名链接属于后者：<img> 发不出 Authorization 头，所以它本身就是免登录的，
 * 安全性由 sign + exp 保证，而不是靠前端藏起来。
 */
function apiUrl(url: string): string {
  if (!url) return ''
  if (url.startsWith('http')) return url
  if (url.startsWith('/system')) {
    return (import.meta.env.VITE_API_PREFIX || '/api') + url
  }
  return url
}

function fileUrl(row: SysFile): string {
  return apiUrl(row.fileUrl)
}

function originUrl(row: SysFile): string {
  // 没生成过压缩件的图片，展示件就是原图本身，二者同一个地址
  return apiUrl(row.originUrl || row.fileUrl)
}

async function viewOrigin(row: SysFile): Promise<void> {
  window.open(originUrl(row), '_blank')
}

async function copyUrl(url: string): Promise<void> {
  const full = url.startsWith('http') ? url : window.location.origin + url
  try {
    await navigator.clipboard.writeText(full)
    ElMessage.success('已复制访问地址')
  } catch {
    // 非 HTTPS 或用户禁用剪贴板权限时的兜底
    ElMessage.info(full)
  }
}

function isImage(row: SysFile): boolean {
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes((row.ext ?? '').toLowerCase())
}

function formatSize(size: number): string {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
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

.file__thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  vertical-align: middle;
}

/* 展示件大小下面补一行原图体积，方便一眼看出压了多少 */
.file__origin-size {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
