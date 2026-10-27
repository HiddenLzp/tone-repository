<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button v-hasPerm="'system:config:add'" type="primary" :icon="Plus" @click="openAdd">
        新增
      </el-button>
      <el-button
        v-hasPerm="'system:config:remove'"
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
      <el-table-column prop="configName" label="参数名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="configKey" label="参数键名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="configValue" label="参数键值" min-width="140" show-overflow-tooltip />
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag :type="row.configType === 1 ? 'danger' : 'info'" size="small">
            {{ row.configType === 1 ? '系统内置' : '自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'system:config:edit'" link type="primary" @click="openEdit(row)">
            修改
          </el-button>
          <el-button
            v-hasPerm="'system:config:remove'"
            link
            type="danger"
            :disabled="row.configType === 1"
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
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <!-- 新增/修改 -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '修改参数' : '新增参数'"
      width="560px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如：用户初始密码" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input
            v-model="form.configKey"
            :disabled="form.configType === 1"
            placeholder="如：sys.user.initPassword"
          />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="如：123456" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-radio-group v-model="form.configType" :disabled="!!form.id">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="说明该参数的用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { pageConfigs, addConfig, updateConfig, deleteConfigs } from '@/api/system'
import type { SysConfig, SysConfigForm } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'

const loading = ref(false)
const submitting = ref(false)
const records = ref<SysConfig[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  configName: '',
  configKey: '',
  configType: undefined as number | undefined
})

const fields: BaseSearchField[] = [
  { prop: 'configName', label: '参数名称', type: 'input', width: 160 },
  { prop: 'configKey', label: '参数键名', type: 'input', width: 180 },
  {
    prop: 'configType',
    label: '内置',
    type: 'select',
    width: 110,
    options: [{ label: '是', value: 1 }, { label: '否', value: 0 }]
  }
]

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysConfigForm>({
  id: undefined,
  configName: '',
  configKey: '',
  configValue: '',
  configType: 0,
  remark: ''
})

const rules: FormRules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [
    { required: true, message: '请输入参数键名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9._]*$/, message: '需字母开头，可含数字、点、下划线', trigger: 'blur' }
  ],
  configValue: [{ required: true, message: '请输入参数键值', trigger: 'blur' }]
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageConfigs({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: SysConfig[]): void {
  // 内置参数不允许删除，从可删集合里排除，避免用户勾半天点删除才报错
  selectedIds.value = rows.filter((r) => r.configType !== 1).map((r) => r.id)
}

function openAdd(): void {
  Object.assign(form, {
    id: undefined,
    configName: '',
    configKey: '',
    configValue: '',
    configType: 0,
    remark: ''
  })
  formVisible.value = true
}

function openEdit(row: SysConfig): void {
  Object.assign(form, {
    id: row.id,
    configName: row.configName,
    configKey: row.configKey,
    configValue: row.configValue ?? '',
    configType: row.configType ?? 0,
    remark: row.remark ?? ''
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) {
      await updateConfig(form)
    } else {
      await addConfig(form)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysConfig): Promise<void> {
  if (row.configType === 1) {
    ElMessage.warning('系统内置参数不允许删除')
    return
  }
  await ElMessageBox.confirm(`确定删除参数 ${row.configName} 吗？`, '提示', { type: 'warning' })
  await deleteConfigs(String(row.id))
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 个参数吗？（系统内置参数会被自动跳过）`,
    '提示',
    { type: 'warning' }
  )
  await deleteConfigs(selectedIds.value.join(','))
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
