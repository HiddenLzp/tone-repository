<template>
  <div class="page">
    <BaseSearch :fields="fields" :model="query" :paged="false" @search="loadData" @reset="loadData">
      <template #extra>
        <el-button :icon="Sort" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
        </el-button>
      </template>
    </BaseSearch>

    <div class="base-toolbar">
      <el-button v-hasPerm="'system:dept:add'" type="primary" :icon="Plus" @click="openAdd()">新增</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="treeData"
      row-key="id"
      border
      :default-expand-all="expanded"
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="deptName" label="部门名称" min-width="200" />
      <el-table-column prop="orderNum" label="排序" width="70" />
      <el-table-column prop="leader" label="负责人" width="100" />
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'system:dept:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button v-hasPerm="'system:dept:add'" link type="success" @click="openAdd(row)">新增下级</el-button>
          <el-button v-hasPerm="'system:dept:remove'" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '修改部门' : '新增部门'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="treeData"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="顶级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" />
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="部门状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
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
import { Plus, Sort } from '@element-plus/icons-vue'
import { listDepts, addDept, updateDept, deleteDept } from '@/api/system'
import type { SysDept, SysDeptForm } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_NORMAL_OPTIONS } from '@/components/BaseSearch/types'

const loading = ref(false)
const submitting = ref(false)
const treeData = ref<SysDept[]>([])
const expanded = ref(true)

const query = reactive({ deptName: '', status: undefined as number | undefined })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysDeptForm>({
  id: undefined,
  parentId: 0,
  deptName: '',
  leader: '',
  phone: '',
  email: '',
  orderNum: 0,
  status: 0
})

const fields: BaseSearchField[] = [
  { prop: 'deptName', label: '部门名称', type: 'input', width: 160 },
  { prop: 'status', label: '状态', type: 'select', width: 110, options: STATUS_NORMAL_OPTIONS }
]

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入排序', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    treeData.value = buildTree(await listDepts(query))
  } finally {
    loading.value = false
  }
}

/** 后端返回平铺列表 + ancestors，这里按 parentId 组树给表格用 */
function buildTree(list: SysDept[]): SysDept[] {
  const map = new Map<number, SysDept>()
  list.forEach((d) => map.set(d.id, { ...d, children: [] }))
  const roots: SysDept[] = []
  map.forEach((node) => {
    const parent = map.get(node.parentId)
    if (parent && parent.id !== node.id) {
      parent.children?.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

function openAdd(parent?: SysDept): void {
  Object.assign(form, {
    id: undefined,
    parentId: parent?.id ?? 0,
    deptName: '',
    leader: '',
    phone: '',
    email: '',
    orderNum: 0,
    status: 0
  })
  formVisible.value = true
}

function openEdit(row: SysDept): void {
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    deptName: row.deptName,
    leader: row.leader ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    orderNum: row.orderNum ?? 0,
    status: row.status ?? 0
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateDept({ ...form })
    } else {
      await addDept({ ...form })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysDept): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除「${row.deptName}」吗？存在下级部门或用户时不允许删除。`,
    '提示',
    { type: 'warning' }
  )
  await deleteDept(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
