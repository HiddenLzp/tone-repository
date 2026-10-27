<template>
  <div class="page">
    <BaseSearch :fields="fields" :model="query" @search="loadData" @reset="loadData" />

    <div class="base-toolbar">
      <el-button v-hasPerm="'system:role:add'" type="primary" :icon="Plus" @click="openAdd">新增</el-button>
      <div class="base-toolbar__spacer" />
      <el-button :icon="Refresh" circle @click="loadData" />
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleCode" label="角色编码" min-width="140" />
      <el-table-column prop="roleSort" label="排序" width="80" />
      <el-table-column label="数据范围" min-width="120">
        <template #default="{ row }">{{ dataScopeText(row.dataScope) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'system:role:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button v-hasPerm="'system:role:assignPerm'" link type="success" @click="openAssignMenu(row)">
            分配权限
          </el-button>
          <el-button v-hasPerm="'system:role:remove'" link type="danger" @click="handleDelete(row)">
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
    <el-dialog v-model="formVisible" :title="form.id ? '修改角色' : '新增角色'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 COMMON_USER" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.roleSort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="item in dataScopes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 自定义数据范围时指定可见部门 -->
        <el-form-item v-if="form.dataScope === 2" label="可见部门">
          <el-tree-select
            v-model="form.deptIds"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            show-checkbox
            check-strictly
            multiple
            collapse-tags
            collapse-tags-tooltip
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-drawer v-model="menuVisible" title="分配权限" size="520px" destroy-on-close>
      <div class="perm">
        <!-- 图例：目录 / 页面 / 按钮(接口) 三种资源共用一张表，必须让用户分辨得出来 -->
        <div class="perm__legend">
          <el-tag type="info" size="small" effect="light">目录</el-tag>
          <el-tag type="success" size="small" effect="light">页面</el-tag>
          <el-tag type="warning" size="small" effect="light">按钮 / 接口</el-tag>
        </div>

        <div class="perm__bar">
          <el-input
            v-model="filterText"
            placeholder="按名称或权限标识筛选"
            clearable
            size="small"
          />
          <span class="perm__count">已选 {{ pickedCount.page }} 个页面 / {{ pickedCount.button }} 个按钮</span>
        </div>

        <el-tree
          :key="treeKey"
          ref="menuTreeRef"
          :data="menuTree"
          :props="{ label: 'menuName', children: 'children' }"
          node-key="id"
          show-checkbox
          default-expand-all
          :default-checked-keys="checkedMenuIds"
          :filter-node-method="filterNode"
          @check="refreshPickedCount"
        >
          <template #default="{ data }">
            <span class="perm-node">
              <span class="perm-node__name">{{ data.menuName }}</span>
              <el-tag :type="menuTagType(data.menuType)" size="small" effect="plain">
                {{ menuTypeText(data.menuType) }}
              </el-tag>
              <!-- 按钮的接口标识直接写在旁边，不用点开详情才能知道勾的是什么 -->
              <span v-if="data.menuType === 3 && data.perms" class="perm-node__perms">
                {{ data.perms }}
              </span>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="menuVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitMenus">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type Tree from 'element-plus/es/components/tree/src/tree.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  pageRoles,
  addRole,
  updateRole,
  deleteRole,
  assignRoleMenus,
  assignRoleDepts,
  listRoleDeptIds,
  getMenuTree,
  listRoleMenuIds,
  listDepts
} from '@/api/system'
import type { SysRole, SysRoleForm, SysDept, MenuNode } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_NORMAL_OPTIONS } from '@/components/BaseSearch/types'

const loading = ref(false)
const submitting = ref(false)
const records = ref<SysRole[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  roleName: '',
  roleCode: '',
  status: undefined as number | undefined
})

const fields: BaseSearchField[] = [
  { prop: 'roleName', label: '角色名称', type: 'input', width: 160 },
  { prop: 'roleCode', label: '角色编码', type: 'input', width: 160 },
  { prop: 'status', label: '状态', type: 'select', width: 110, options: STATUS_NORMAL_OPTIONS }
]

const dataScopes = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '自定义部门' },
  { value: 3, label: '仅本部门' },
  { value: 4, label: '本部门及以下' },
  { value: 5, label: '仅本人' }
]

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysRoleForm>({
  id: undefined,
  roleName: '',
  roleCode: '',
  roleSort: 1,
  dataScope: 1,
  status: 0,
  remark: '',
  deptIds: [] as number[]
})

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

// 权限分配
const menuVisible = ref(false)
const menuTree = ref<MenuNode[]>([])
const checkedMenuIds = ref<number[]>([])
const currentRoleId = ref<number>()
const menuTreeRef = ref<InstanceType<typeof Tree>>()
/** 每次打开抽屉 +1，用 key 强制重建 el-tree，避免上一次的勾选状态残留 */
const treeKey = ref(0)
const deptTree = ref<SysDept[]>([])
/** 权限树筛选关键字 */
const filterText = ref('')
/** id → 资源类型 / 是否叶子，勾选统计要用 */
const menuTypeMap = new Map<number, number>()
const leafIdSet = new Set<number>()
const pickedCount = ref({ page: 0, button: 0 })

function dataScopeText(scope?: number): string {
  return dataScopes.find((s) => s.value === scope)?.label || '-'
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageRoles({ ...query })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openAdd(): void {
  Object.assign(form, {
    id: undefined,
    roleName: '',
    roleCode: '',
    roleSort: 1,
    dataScope: 1,
    status: 0,
    remark: '',
    deptIds: []
  })
  formVisible.value = true
}

async function openEdit(row: SysRole): Promise<void> {
  Object.assign(form, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    roleSort: row.roleSort ?? 1,
    dataScope: row.dataScope ?? 1,
    status: row.status ?? 0,
    remark: row.remark ?? '',
    deptIds: [] as number[]
  })
  // 先弹窗再拉部门，避免等待时界面卡住
  formVisible.value = true
  // 自定义数据范围必须回显已配置的部门，否则一保存就把已有配置清空
  form.deptIds = row.dataScope === 2 ? await listRoleDeptIds(row.id) : []
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateRole({ ...form })
    } else {
      // 关键：回写新增出来的 id，否则下面的部门分配没有目标
      form.id = await addRole({ ...form })
    }
    // 自定义数据范围时单独提交部门：角色表本身不存部门关系。
    // 非自定义时也提交一次（空数组），把历史部门关系清干净，
    // 否则「改成全部 → 再改回自定义」会把旧配置重新捞出来
    await assignRoleDepts(form.id, form.dataScope === 2 ? (form.deptIds ?? []) : [])
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function openAssignMenu(row: SysRole): Promise<void> {
  currentRoleId.value = row.id
  filterText.value = ''
  const [tree, ids] = await Promise.all([getMenuTree(), listRoleMenuIds(row.id)])
  menuTree.value = tree
  // 顺带把「id → 类型」和「叶子 id」记下来，勾选统计要用
  menuTypeMap.clear()
  leafIdSet.clear()
  indexMenuTypes(tree)
  // 关键：回显前必须剔除所有非叶子节点 id。
  // el-tree 在父子联动（check-strictly=false）模式下，default-checked-keys 里的父节点
  // 会走 setChecked(true, deep=true) 把子孙全部勾上 —— 表现就是「明明只勾了一项，
  // 打开抽屉却是全选」。父节点的状态交给 el-tree 自己按子节点推导即可，
  // 视觉上该全选它会全选、该半选它会半选，但不会反过来污染数据。
  const parentIds = new Set<number>()
  collectParentIds(tree, parentIds)
  checkedMenuIds.value = ids.filter((id) => !parentIds.has(id))
  // 换 key 强制重建树，清掉上一次打开时残留的勾选/展开状态
  treeKey.value++
  menuVisible.value = true
  // 树是 key 重建的，要等这一轮渲染完才拿得到勾选状态
  nextTick(refreshPickedCount)
}

/** 收集所有「有子节点」的节点 id */
function collectParentIds(nodes: MenuNode[], out: Set<number>): void {
  nodes.forEach((n) => {
    if (n.children?.length) {
      out.add(n.id)
      collectParentIds(n.children, out)
    }
  })
}

/** 铺平记录每个节点的类型，并标出哪些是叶子 */
function indexMenuTypes(nodes: MenuNode[]): void {
  nodes.forEach((n) => {
    menuTypeMap.set(n.id, n.menuType)
    if (n.children?.length) {
      indexMenuTypes(n.children)
    } else {
      leafIdSet.add(n.id)
    }
  })
}

/** 目录 / 页面 / 按钮 的文字与配色，与菜单管理页保持一致 */
function menuTypeText(type: number): string {
  if (type === 1) return '目录'
  if (type === 2) return '页面'
  return '按钮'
}

function menuTagType(type: number): 'info' | 'success' | 'warning' {
  if (type === 1) return 'info'
  if (type === 2) return 'success'
  return 'warning'
}

/** 筛选：名称或权限标识命中即可，命中子节点的父节点会自动保留 */
function filterNode(value: string, data: MenuNode): boolean {
  if (!value) return true
  const keyword = value.toLowerCase()
  return (
    data.menuName.toLowerCase().includes(keyword) ||
    (data.perms ?? '').toLowerCase().includes(keyword)
  )
}

/**
 * 统计已勾选的页面数与按钮数。
 * 只数叶子：父节点（目录、带子页面的菜单）的勾选状态是由子孙推导出来的，
 * 重复计数会让"已选 3 个页面"变成"已选 12 个"，反而看不懂。
 */
function refreshPickedCount(): void {
  const tree = menuTreeRef.value
  const keys = [
    ...((tree?.getCheckedKeys() ?? []) as number[]),
    ...((tree?.getHalfCheckedKeys() ?? []) as number[])
  ]
  let page = 0
  let button = 0
  keys.forEach((id) => {
    if (!leafIdSet.has(id)) return
    const type = menuTypeMap.get(id)
    if (type === 2) page++
    else if (type === 3) button++
  })
  pickedCount.value = { page, button }
}

async function submitMenus(): Promise<void> {
  if (!currentRoleId.value) return
  const tree = menuTreeRef.value
  // 半选节点（父节点）也要提交，否则子菜单选中了父目录没选中，侧边栏会展开不了
  const checked = [...(tree?.getCheckedKeys() ?? []), ...(tree?.getHalfCheckedKeys() ?? [])]
  submitting.value = true
  try {
    await assignRoleMenus(currentRoleId.value, checked as number[])
    ElMessage.success('分配成功')
    menuVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysRole): Promise<void> {
  await ElMessageBox.confirm(`确定删除角色 ${row.roleName} 吗？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  const depts = await listDepts()
  deptTree.value = buildDeptTree(depts)
  loadData()
})

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

// 筛选关键字变化时交给 el-tree 自己过滤（内部实现会保留命中节点的祖先）
watch(filterText, (val) => {
  menuTreeRef.value?.filter(val)
})
</script>

<style scoped lang="scss">
.page {
  &__pagination {
    margin-top: 12px;
    justify-content: flex-end;
  }
}

/* 分配权限抽屉 */
.perm {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__legend {
    display: flex;
    gap: 6px;
    margin-bottom: 10px;
  }

  &__bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
  }

  &__count {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  :deep(.el-tree) {
    flex: 1;
    min-height: 0;
    overflow: auto;
  }
}

.perm-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 8px;

  &__name {
    color: var(--el-text-color-regular);
  }

  /* 接口标识：按钮节点最需要区分的信息，弱化显示不抢主名 */
  &__perms {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    font-family: Consolas, Monaco, monospace;
  }
}
</style>
