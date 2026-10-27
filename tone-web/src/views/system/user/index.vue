<template>
  <div class="page">
    <!-- 搜索区 -->
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <!-- 操作栏 -->
    <div class="base-toolbar">
      <el-button v-hasPerm="'system:user:add'" type="primary" :icon="Plus" @click="openAdd">
        新增
      </el-button>
      <el-button
        v-hasPerm="'system:user:remove'"
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
      <el-table-column prop="username" label="账号" min-width="110" />
      <el-table-column prop="nickname" label="昵称" min-width="110" />
      <el-table-column prop="deptName" label="部门" min-width="140" />
      <el-table-column prop="phone" label="手机号" min-width="120" />
      <el-table-column label="性别" width="70">
        <template #default="{ row }">{{ sexText(row.sex) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="0"
            :inactive-value="1"
            :disabled="!hasPerm('system:user:edit')"
            @change="(val: number | boolean) => handleStatusChange(row, Number(val))"
          />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" min-width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'system:user:edit'" link type="primary" @click="openEdit(row)">
            修改
          </el-button>
          <el-button v-hasPerm="'system:user:resetPwd'" link type="warning" @click="handleResetPwd(row)">
            重置密码
          </el-button>
          <el-button v-hasPerm="'system:user:assignRole'" link type="success" @click="openAssignRoles(row)">
            分配角色
          </el-button>
          <el-button v-hasPerm="'system:user:remove'" link type="danger" @click="handleDelete(row)">
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
    <el-dialog v-model="formVisible" :title="form.id ? '修改用户' : '新增用户'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="账号" prop="username">
              <el-input v-model="form.username" :disabled="!!form.id" placeholder="字母/数字/下划线" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名">
              <el-input v-model="form.realName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', children: 'children' }"
                node-key="id"
                check-strictly
                clearable
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.sex" style="width: 100%">
                <el-option label="男" :value="0" />
                <el-option label="女" :value="1" />
                <el-option label="未知" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">正常</el-radio>
                <el-radio :value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple style="width: 100%">
                <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
              </el-select>
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

    <!-- 分配角色 -->
    <el-dialog v-model="roleVisible" title="分配角色" width="420px">
      <el-select v-model="assignRoleIds" multiple style="width: 100%">
        <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="roleVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitRoles">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Delete } from '@element-plus/icons-vue'
import {
  pageUsers,
  getUser,
  addUser,
  updateUser,
  deleteUsers,
  changeUserStatus,
  resetUserPassword,
  assignUserRoles,
  listRoleOptions,
  listDepts
} from '@/api/system'
import { hasPerm } from '@/directives'
import type { SysUser, SysDept, SysRole, UserSaveRequest } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_NORMAL_OPTIONS } from '@/components/BaseSearch/types'

const loading = ref(false)
const submitting = ref(false)
const records = ref<SysUser[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const deptTree = ref<SysDept[]>([])
const roleOptions = ref<SysRole[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  phone: '',
  status: undefined as number | undefined,
  deptId: undefined as number | undefined
})

const fields = computed<BaseSearchField[]>(() => [
  { prop: 'keyword', label: '关键字', type: 'input', placeholder: '账号/昵称', width: 160 },
  { prop: 'phone', label: '手机号', type: 'input', width: 140 },
  { prop: 'status', label: '状态', type: 'select', width: 110, options: STATUS_NORMAL_OPTIONS },
  {
    prop: 'deptId',
    label: '部门',
    type: 'tree-select',
    width: 180,
    treeData: deptTree.value,
    treeProps: { label: 'deptName', children: 'children' },
    nodeKey: 'id',
    checkStrictly: true
  }
])

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserSaveRequest>({
  id: undefined,
  username: '',
  nickname: '',
  realName: '',
  deptId: undefined,
  phone: '',
  email: '',
  sex: 2,
  status: 0,
  remark: '',
  roleIds: []
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 30, message: '长度需在 2 到 30 位之间', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

// 分配角色
const roleVisible = ref(false)
const assignRoleIds = ref<number[]>([])
const assignUserId = ref<number>()

function sexText(sex?: number): string {
  if (sex === 0) return '男'
  if (sex === 1) return '女'
  return '未知'
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageUsers({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: SysUser[]): void {
  selectedIds.value = rows.map((r) => r.id)
}

function openAdd(): void {
  Object.assign(form, {
    id: undefined,
    username: '',
    nickname: '',
    realName: '',
    deptId: undefined,
    phone: '',
    email: '',
    sex: 2,
    status: 0,
    remark: '',
    roleIds: []
  })
  formVisible.value = true
}

async function openEdit(row: SysUser): Promise<void> {
  // 走详情接口：列表只带了展示字段，角色ID只有详情里才有
  const detail = await getUser(row.id)
  Object.assign(form, {
    id: detail.id,
    username: detail.username,
    nickname: detail.nickname,
    realName: detail.realName,
    deptId: detail.deptId,
    phone: detail.phone,
    email: detail.email,
    sex: detail.sex,
    status: detail.status,
    remark: detail.remark,
    roleIds: detail.roleIds || []
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateUser({ ...form })
      ElMessage.success('修改成功')
    } else {
      await addUser({ ...form })
      ElMessage.success('新增成功，初始密码为 123456')
    }
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleStatusChange(row: SysUser, status: number): Promise<void> {
  try {
    await changeUserStatus(row.id, status)
    ElMessage.success(status === 0 ? '已启用' : '已停用')
  } catch {
    // 失败要把开关拨回去，否则界面与实际状态不一致
    row.status = status === 0 ? 1 : 0
  }
}

async function handleResetPwd(row: SysUser): Promise<void> {
  await ElMessageBox.confirm(
    `确定将 ${row.username} 的密码重置为初始密码吗？重置后该用户首次登录必须修改密码。`,
    '提示',
    { type: 'warning' }
  )
  await resetUserPassword(row.id)
  ElMessage.success('密码已重置为 123456')
}

async function handleDelete(row: SysUser): Promise<void> {
  await ElMessageBox.confirm(`确定删除用户 ${row.username} 吗？`, '提示', { type: 'warning' })
  await deleteUsers(String(row.id))
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete(): Promise<void> {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个用户吗？`, '提示', {
    type: 'warning'
  })
  await deleteUsers(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  loadData()
}

async function openAssignRoles(row: SysUser): Promise<void> {
  const detail = await getUser(row.id)
  assignUserId.value = row.id
  assignRoleIds.value = detail.roleIds || []
  roleVisible.value = true
}

async function submitRoles(): Promise<void> {
  if (!assignUserId.value) return
  submitting.value = true
  try {
    await assignUserRoles(assignUserId.value, assignRoleIds.value)
    ElMessage.success('分配成功')
    roleVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 部门树和角色选项一次拉好，新增/修改/查询都要用
  const [depts, roles] = await Promise.all([listDepts(), listRoleOptions()])
  deptTree.value = buildDeptTree(depts)
  roleOptions.value = roles
  loadData()
})

/** 后端 /system/dept/list 返回的是平铺列表，前端按 parentId 组树 */
function buildDeptTree(list: SysDept[]): SysDept[] {
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
</script>

<style scoped lang="scss">
.page {
  &__pagination {
    margin-top: 12px;
    justify-content: flex-end;
  }
}
</style>
