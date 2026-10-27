<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button v-hasPerm="'job:task:add'" type="primary" :icon="Plus" @click="openAdd">
        新增
      </el-button>
      <el-button
        v-hasPerm="'job:task:remove'"
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
      <el-table-column prop="cron" label="cron 表达式" width="150" />
      <el-table-column label="并发" width="80">
        <template #default="{ row }">
          <el-tag :type="row.concurrent === 1 ? 'warning' : 'info'" size="small">
            {{ row.concurrent === 1 ? '禁止' : '允许' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="0"
            :inactive-value="1"
            :disabled="!hasPerm('job:task:changeStatus')"
            @change="(val: number | boolean) => handleStatusChange(row, Number(val))"
          />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'job:task:edit'" link type="primary" @click="openEdit(row)">
            修改
          </el-button>
          <el-button v-hasPerm="'job:task:run'" link type="success" @click="handleRun(row)">
            执行一次
          </el-button>
          <el-button v-hasPerm="'job:task:remove'" link type="danger" @click="handleDelete(row)">
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

    <!-- 新增/修改 -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '修改任务' : '新增任务'"
      width="600px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
              <el-input v-model="form.jobName" placeholder="如：清理操作日志" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务分组" prop="jobGroup">
              <el-input v-model="form.jobGroup" placeholder="DEFAULT" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="调用目标" prop="invokeTarget">
              <el-input
                v-model="form.invokeTarget"
                placeholder="bean.method(参数)，如 logCleanTask.cleanOperationLog()"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="cron" prop="cron">
              <el-input v-model="form.cron" placeholder="如 0 0 3 * * ?（每天凌晨 3 点）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="错过策略">
              <el-select v-model="form.misfirePolicy" style="width: 100%">
                <el-option label="立即执行" :value="1" />
                <el-option label="执行一次" :value="2" />
                <el-option label="放弃执行" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="并发执行">
              <el-radio-group v-model="form.concurrent">
                <el-radio :value="0">允许</el-radio>
                <el-radio :value="1">禁止</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">正常</el-radio>
                <el-radio :value="1">暂停</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
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
import {
  pageJobs,
  addJob,
  updateJob,
  deleteJobs,
  changeJobStatus,
  runJob
} from '@/api/job'
import { hasPerm } from '@/directives'
import type { SysJob, SysJobForm } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'

const loading = ref(false)
const submitting = ref(false)
const records = ref<SysJob[]>([])
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
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    width: 100,
    options: [{ label: '正常', value: 0 }, { label: '暂停', value: 1 }]
  }
]

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysJobForm>({
  id: undefined,
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cron: '',
  misfirePolicy: 1,
  concurrent: 1,
  status: 0,
  remark: ''
})

const rules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  jobGroup: [{ required: true, message: '请输入任务分组', trigger: 'blur' }],
  invokeTarget: [
    { required: true, message: '请输入调用目标', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9]*\.[a-zA-Z][a-zA-Z0-9]*\(.*\)$/,
      message: '格式应为 bean.method(参数)，无参也要带括号',
      trigger: 'blur'
    }
  ],
  cron: [{ required: true, message: '请输入 cron 表达式', trigger: 'blur' }]
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageJobs({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: SysJob[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

function openAdd(): void {
  Object.assign(form, {
    id: undefined,
    jobName: '',
    jobGroup: 'DEFAULT',
    invokeTarget: '',
    cron: '',
    misfirePolicy: 1,
    concurrent: 1,
    status: 0,
    remark: ''
  })
  formVisible.value = true
}

function openEdit(row: SysJob): void {
  Object.assign(form, {
    id: row.id,
    jobName: row.jobName,
    jobGroup: row.jobGroup,
    invokeTarget: row.invokeTarget,
    cron: row.cron,
    misfirePolicy: row.misfirePolicy ?? 1,
    concurrent: row.concurrent ?? 1,
    status: row.status ?? 0,
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
      await updateJob(form)
    } else {
      await addJob(form)
    }
    ElMessage.success('保存成功，调度已按新的 cron 重新注册')
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleStatusChange(row: SysJob, status: number): Promise<void> {
  try {
    await changeJobStatus(row.id, status)
    ElMessage.success(status === 0 ? '任务已启动' : '任务已暂停')
  } catch {
    // 失败要把开关拨回去，否则界面状态和实际调度不一致
    row.status = status === 0 ? 1 : 0
  }
}

async function handleRun(row: SysJob): Promise<void> {
  await ElMessageBox.confirm(`确定立即执行一次任务 ${row.jobName} 吗？`, '提示', {
    type: 'warning'
  })
  await runJob(row.id)
  ElMessage.success('已触发，执行结果请看任务日志')
}

async function handleDelete(row: SysJob): Promise<void> {
  await ElMessageBox.confirm(`确定删除任务 ${row.jobName} 吗？`, '提示', { type: 'warning' })
  await deleteJobs(String(row.id))
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 个任务吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteJobs(selectedIds.value.join(','))
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
