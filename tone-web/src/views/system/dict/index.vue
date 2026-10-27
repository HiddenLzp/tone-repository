<template>
  <!-- 内部已是左右双卡布局，页面容器用 --plain 还原成透明壳 -->
  <div class="page page--plain dict">
    <el-row :gutter="12" class="dict__row">
      <!-- ============ 左：字典类型 ============ -->
      <el-col :span="9" class="dict__col">
        <el-card shadow="never" class="dict-card">
          <template #header>
            <div class="dict-card__header">
              <span class="dict-card__title">字典类型</span>
              <div class="dict-card__actions">
                <el-button
                  v-hasPerm="'system:dict:add'"
                  type="primary"
                  :icon="Plus"
                  @click="openTypeAdd"
                >
                  新增
                </el-button>
                <el-tooltip content="刷新" placement="top">
                  <el-button :icon="Refresh" circle @click="loadTypes" />
                </el-tooltip>
              </div>
            </div>
          </template>

          <BaseSearch
            plain
            :fields="typeFields"
            :model="typeQuery"
            @search="searchType"
            @reset="searchType"
          />

          <div class="dict-card__table">
            <el-table
              ref="typeTableRef"
              v-loading="typeLoading"
              :data="typeRecords"
              height="100%"
              border
              size="small"
              highlight-current-row
              @current-change="onTypeChange"
            >
              <el-table-column prop="dictName" label="字典名称" min-width="100" />
              <el-table-column prop="dictType" label="字典类型" min-width="120" />
              <el-table-column label="状态" width="66">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                    {{ row.status === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="108" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-hasPerm="'system:dict:edit'"
                    link
                    type="primary"
                    @click.stop="openTypeEdit(row)"
                  >
                    修改
                  </el-button>
                  <el-button
                    v-hasPerm="'system:dict:remove'"
                    link
                    type="danger"
                    @click.stop="handleTypeDelete(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-pagination
            class="dict__pagination"
            small
            v-model:current-page="typeQuery.pageNum"
            v-model:page-size="typeQuery.pageSize"
            :total="typeTotal"
            layout="total, prev, pager, next"
            @change="loadTypes"
          />
        </el-card>
      </el-col>

      <!-- ============ 右：选中类型的字典数据 ============ -->
      <el-col :span="15" class="dict__col">
        <el-card shadow="never" class="dict-card">
          <template #header>
            <div class="dict-card__header">
              <span class="dict-card__title">
                {{ currentType ? `字典数据 · ${currentType.dictName}` : '请先选择左侧字典类型' }}
              </span>
              <el-tag v-if="currentType" size="small" type="info">
                {{ currentType.dictType }}
              </el-tag>
              <div class="dict-card__actions">
                <el-button
                  v-hasPerm="'system:dict:add'"
                  type="primary"
                  :icon="Plus"
                  :disabled="!currentType"
                  @click="openDataAdd"
                >
                  新增
                </el-button>
                <el-button
                  v-hasPerm="'system:dict:remove'"
                  type="danger"
                  :icon="Delete"
                  :disabled="!selectedDataIds.length"
                  @click="handleDataBatchDelete"
                >
                  批量删除
                </el-button>
                <el-tooltip content="刷新" placement="top">
                  <el-button :icon="Refresh" circle :disabled="!currentType" @click="searchData" />
                </el-tooltip>
              </div>
            </div>
          </template>

          <BaseSearch
            plain
            :fields="dataFields"
            :model="dataQuery"
            @search="searchData"
            @reset="searchData"
          />

          <div class="dict-card__table">
            <el-table
              v-loading="dataLoading"
              :data="dataRecords"
              height="100%"
              border
              size="small"
              @selection-change="onDataSelectionChange"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column prop="dictLabel" label="标签" min-width="100" />
              <el-table-column prop="dictValue" label="键值" min-width="100" />
              <el-table-column prop="dictSort" label="排序" width="60" />
              <el-table-column label="默认" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 0" type="success" size="small">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="样式" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.listClass" :type="tagType(row.listClass)" size="small">
                    {{ row.listClass }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="66">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                    {{ row.status === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
              <el-table-column label="操作" width="108" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-hasPerm="'system:dict:edit'"
                    link
                    type="primary"
                    @click="openDataEdit(row)"
                  >
                    修改
                  </el-button>
                  <el-button
                    v-hasPerm="'system:dict:remove'"
                    link
                    type="danger"
                    @click="handleDataDelete(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-pagination
            class="dict__pagination"
            v-model:current-page="dataQuery.pageNum"
            v-model:page-size="dataQuery.pageSize"
            :total="dataTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @change="loadData"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型表单 -->
    <el-dialog
      v-model="typeFormVisible"
      :title="typeForm.id ? '修改字典类型' : '新增字典类型'"
      width="520px"
      append-to-body
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="80px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="如：用户性别" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            :disabled="!!typeForm.id"
            placeholder="如：sys_user_sex，建议使用下划线小写"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="typeFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitTypeForm">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 字典数据表单 -->
    <el-dialog
      v-model="dataFormVisible"
      :title="dataForm.id ? '修改字典数据' : '新增字典数据'"
      width="520px"
      append-to-body
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="80px">
        <el-form-item label="所属类型">
          <el-input :model-value="currentType?.dictType ?? ''" disabled />
        </el-form-item>
        <el-form-item label="标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="展示用文字，如：男" />
        </el-form-item>
        <el-form-item label="键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="存储用值，如：0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="是否默认">
          <el-radio-group v-model="dataForm.isDefault">
            <el-radio :value="0">是</el-radio>
            <el-radio :value="1">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="dataForm.listClass" clearable placeholder="默认" style="width: 100%">
            <el-option label="primary" value="primary" />
            <el-option label="success" value="success" />
            <el-option label="info" value="info" />
            <el-option label="warning" value="warning" />
            <el-option label="danger" value="danger" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dataForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="dataFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitDataForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Delete } from '@element-plus/icons-vue'
import {
  pageDictTypes,
  addDictType,
  updateDictType,
  deleteDictTypes,
  pageDictData,
  addDictData,
  updateDictData,
  deleteDictData
} from '@/api/system'
import type {
  SysDictType,
  SysDictTypeForm,
  SysDictData,
  SysDictDataForm
} from '@/types/api'
import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_NORMAL_OPTIONS } from '@/components/BaseSearch/types'

const typeTableRef = ref<TableInstance>()
const typeLoading = ref(false)
const dataLoading = ref(false)
const submitting = ref(false)

const typeRecords = ref<SysDictType[]>([])
const typeTotal = ref(0)
const typeQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  dictName: '',
  dictType: '',
  status: undefined as number | undefined
})

const typeFields: BaseSearchField[] = [
  { prop: 'dictName', label: '字典名称', type: 'input', width: 150 },
  { prop: 'dictType', label: '字典类型', type: 'input', width: 150 },
  { prop: 'status', label: '状态', type: 'select', width: 100, options: STATUS_NORMAL_OPTIONS }
]

const dataRecords = ref<SysDictData[]>([])
const dataTotal = ref(0)
const selectedDataIds = ref<number[]>([])
const dataQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  dictType: '',
  dictLabel: '',
  status: undefined as number | undefined
})

const dataFields: BaseSearchField[] = [
  { prop: 'dictLabel', label: '标签', type: 'input', width: 140 },
  { prop: 'status', label: '状态', type: 'select', width: 100, options: STATUS_NORMAL_OPTIONS }
]

const currentType = ref<SysDictType>()

// ============ 字典类型 ============

const typeFormVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive<SysDictTypeForm>({
  id: undefined,
  dictName: '',
  dictType: '',
  status: 0,
  remark: ''
})

const typeRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [
    { required: true, message: '请输入字典类型', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '需小写字母开头，可含数字与下划线', trigger: 'blur' }
  ]
}

async function loadTypes(): Promise<void> {
  typeLoading.value = true
  try {
    const res = await pageDictTypes({ ...typeQuery })
    typeRecords.value = res.records
    typeTotal.value = res.total
    // 首屏默认选中第一条，避免右侧空着不知道发生了什么
    if (!currentType.value && res.records.length) {
      currentType.value = res.records[0]
      dataQuery.dictType = res.records[0].dictType
      loadData()
    }
    await nextTick()
    // 翻页 / 刷新后 highlight-current-row 的选中态会丢，这里补回去
    const hit = typeRecords.value.find((r) => r.id === currentType.value?.id)
    typeTableRef.value?.setCurrentRow(hit)
  } finally {
    typeLoading.value = false
  }
}

function searchType(): void {
  typeQuery.pageNum = 1
  loadTypes()
}

function onTypeChange(row: SysDictType | null): void {
  if (!row || row.id === currentType.value?.id) return
  currentType.value = row
  dataQuery.dictType = row.dictType
  searchData()
}

function openTypeAdd(): void {
  Object.assign(typeForm, { id: undefined, dictName: '', dictType: '', status: 0, remark: '' })
  typeFormVisible.value = true
}

function openTypeEdit(row: SysDictType): void {
  Object.assign(typeForm, {
    id: row.id,
    dictName: row.dictName,
    dictType: row.dictType,
    status: row.status ?? 0,
    remark: row.remark ?? ''
  })
  typeFormVisible.value = true
}

async function submitTypeForm(): Promise<void> {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate()
  submitting.value = true
  try {
    if (typeForm.id) {
      await updateDictType(typeForm)
    } else {
      await addDictType(typeForm)
    }
    ElMessage.success('保存成功')
    typeFormVisible.value = false
    loadTypes()
  } finally {
    submitting.value = false
  }
}

async function handleTypeDelete(row: SysDictType): Promise<void> {
  await ElMessageBox.confirm(
    `删除字典类型 ${row.dictName} 会使其下所有字典数据失效，确定删除吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteDictTypes(String(row.id))
  ElMessage.success('删除成功')
  if (currentType.value?.id === row.id) {
    currentType.value = undefined
    dataQuery.dictType = ''
    dataRecords.value = []
    dataTotal.value = 0
  }
  loadTypes()
}

// ============ 字典数据 ============

const dataFormVisible = ref(false)
const dataFormRef = ref<FormInstance>()
const dataForm = reactive<SysDictDataForm>({
  id: undefined,
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 1,
  isDefault: 1,
  listClass: '',
  status: 0,
  remark: ''
})

const dataRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入键值', trigger: 'blur' }]
}

function searchData(): void {
  dataQuery.pageNum = 1
  loadData()
}

async function loadData(): Promise<void> {
  if (!dataQuery.dictType) {
    dataRecords.value = []
    dataTotal.value = 0
    return
  }
  dataLoading.value = true
  try {
    const res = await pageDictData({ ...dataQuery })
    dataRecords.value = res.records
    dataTotal.value = res.total
  } finally {
    dataLoading.value = false
  }
}

function onDataSelectionChange(rows: SysDictData[]): void {
  selectedDataIds.value = rows.map((r) => r.id)
}

function openDataAdd(): void {
  if (!currentType.value) return
  Object.assign(dataForm, {
    id: undefined,
    dictType: currentType.value.dictType,
    dictLabel: '',
    dictValue: '',
    dictSort: dataRecords.value.length + 1,
    isDefault: 1,
    listClass: '',
    status: 0,
    remark: ''
  })
  dataFormVisible.value = true
}

function openDataEdit(row: SysDictData): void {
  Object.assign(dataForm, {
    id: row.id,
    dictType: row.dictType,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    dictSort: row.dictSort ?? 0,
    isDefault: row.isDefault ?? 1,
    listClass: row.listClass ?? '',
    status: row.status ?? 0,
    remark: row.remark ?? ''
  })
  dataFormVisible.value = true
}

async function submitDataForm(): Promise<void> {
  if (!dataFormRef.value) return
  await dataFormRef.value.validate()
  submitting.value = true
  try {
    if (dataForm.id) {
      await updateDictData(dataForm)
    } else {
      await addDictData(dataForm)
    }
    ElMessage.success('保存成功')
    dataFormVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDataDelete(row: SysDictData): Promise<void> {
  await ElMessageBox.confirm(`确定删除字典数据 ${row.dictLabel} 吗？`, '提示', { type: 'warning' })
  await deleteDictData(String(row.id))
  ElMessage.success('删除成功')
  loadData()
}

async function handleDataBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedDataIds.value.length} 条字典数据吗？`,
    '提示',
    { type: 'warning' }
  )
  await deleteDictData(selectedDataIds.value.join(','))
  ElMessage.success('删除成功')
  loadData()
}

/** listClass 取值就是 Element Tag 的 type，直接透传即可 */
function tagType(listClass?: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  const allowed = ['primary', 'success', 'info', 'warning', 'danger']
  return (allowed.includes(listClass ?? '') ? listClass : 'info') as
    | 'primary'
    | 'success'
    | 'info'
    | 'warning'
    | 'danger'
}

onMounted(loadTypes)
</script>

<style scoped lang="scss">
/*
 * 固定布局：整页撑满内容区，左右两张卡片各自内部滚动。
 * .base-layout__content 是定高容器，所以这里 height:100% 拿到的就是可用高度。
 */
.dict {
  height: 100%;

  &__row {
    height: 100%;
    // el-row 的 gutter 会用负 margin 抵消列间距，在 100% 宽的父容器里会撑出横向滚动条
    margin-left: 0 !important;
    margin-right: 0 !important;
  }

  &__col {
    height: 100%;
  }

  &__pagination {
    margin-top: 10px;
    justify-content: flex-end;
    flex-shrink: 0;
  }
}

.dict-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-card__header) {
    padding: 10px 12px;
    flex-shrink: 0;
  }

  /*
   * flex 子项默认 min-height:auto 会被内容撑开导致 overflow 失效，
   * 所以每一层中间容器都要显式 min-height: 0
   */
  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 12px;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__title {
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__table {
    flex: 1;
    min-height: 0;
  }
}
</style>
