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
      <el-button v-hasPerm="'system:menu:add'" type="primary" :icon="Plus" @click="openAdd()">新增</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="treeData"
      row-key="id"
      border
      :default-expand-all="expanded"
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.menuType === 1 ? 'info' : row.menuType === 2 ? 'success' : 'warning'" size="small">
            {{ menuTypeText(row.menuType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderNum" label="排序" width="70" />
      <el-table-column prop="path" label="路由地址" min-width="140" show-overflow-tooltip />
      <el-table-column prop="component" label="组件路径" min-width="160" show-overflow-tooltip />
      <el-table-column prop="perms" label="权限标识" min-width="160" show-overflow-tooltip />
      <el-table-column label="图标" width="130">
        <template #default="{ row }">
          <div v-if="row.icon" class="menu-icon-cell">
            <el-icon :size="16"><component :is="row.icon" /></el-icon>
            <span class="menu-icon-cell__name">{{ row.icon }}</span>
          </div>
          <span v-else class="menu-icon-cell__empty">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPerm="'system:menu:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button v-hasPerm="'system:menu:add'" link type="success" @click="openAdd(row)">新增下级</el-button>
          <el-button v-hasPerm="'system:menu:remove'" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '修改菜单' : '新增菜单'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="顶级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="路由地址" prop="path">
          <el-input v-model="form.path" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如 system/user/index" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 3" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 system:user:add" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="菜单图标">
          <IconSelect v-model="form.icon" />
          <div class="menu-form__hint">
            从图标库里选，不用记图标名；也可以直接在上面搜索框输中文，比如「用户」「设置」
          </div>
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="显示状态">
          <el-radio-group v-model="form.visible">
            <el-radio :value="0">显示</el-radio>
            <el-radio :value="1">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="页面缓存">
          <el-radio-group v-model="form.isCache">
            <el-radio :value="1">缓存</el-radio>
            <el-radio :value="0">不缓存</el-radio>
          </el-radio-group>
          <div class="menu-form__hint">
            缓存后切换页签不会重新加载页面，筛选条件、滚动位置和未关闭的弹框都保留；
            数据实时性要求高的页面可选「不缓存」
          </div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Sort } from '@element-plus/icons-vue'
import { getMenuTree, addMenu, updateMenu, deleteMenu } from '@/api/system'
import type { SysMenu, SysMenuForm, MenuNode } from '@/types/api'

import BaseSearch from '@/components/BaseSearch/index.vue'
import type { BaseSearchField } from '@/components/BaseSearch/types'
import { STATUS_NORMAL_OPTIONS } from '@/components/BaseSearch/types'
import IconSelect from '@/components/IconSelect/index.vue'

const loading = ref(false)
const submitting = ref(false)
const treeData = ref<MenuNode[]>([])
const expanded = ref(true)

const query = reactive({ menuName: '', status: undefined as number | undefined })

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysMenuForm>({
  id: undefined,
  parentId: 0,
  menuName: '',
  menuType: 2,
  path: '',
  component: '',
  perms: '',
  icon: '',
  orderNum: 0,
  visible: 0,
  status: 0,
  isCache: 1
})

const fields: BaseSearchField[] = [
  { prop: 'menuName', label: '菜单名称', type: 'input', width: 160 },
  { prop: 'status', label: '状态', type: 'select', width: 110, options: STATUS_NORMAL_OPTIONS }
]

const rules = computed<FormRules>(() => ({
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  path: form.menuType !== 3 ? [{ required: true, message: '请输入路由地址', trigger: 'blur' }] : [],
  component: form.menuType === 2 ? [{ required: true, message: '请输入组件路径', trigger: 'blur' }] : [],
  perms: form.menuType === 3 ? [{ required: true, message: '请输入权限标识', trigger: 'blur' }] : []
}))

// 上级菜单只能选目录/菜单，按钮不能当父节点
const parentOptions = computed<MenuNode[]>(() => filterDirs(treeData.value))

function filterDirs(nodes: MenuNode[]): MenuNode[] {
  return nodes
    .filter((n) => n.menuType !== 3)
    .map((n) => ({ ...n, children: n.children ? filterDirs(n.children) : [] }))
}

function menuTypeText(type: number): string {
  if (type === 1) return '目录'
  if (type === 2) return '菜单'
  return '按钮'
}

async function loadData(): Promise<void> {
  loading.value = true
  try {
    // 直接用树接口：表格本身就是树形展示，平铺列表还要自己再组一次
    treeData.value = await getMenuTree(query)
  } finally {
    loading.value = false
  }
}

function openAdd(parent?: MenuNode | SysMenu): void {
  Object.assign(form, {
    id: undefined,
    parentId: parent?.id ?? 0,
    menuName: '',
    menuType: 2,
    path: '',
    component: '',
    perms: '',
    icon: '',
    orderNum: 0,
    visible: 0,
    status: 0,
    // 默认缓存：多页签下不缓存等于每次切回来都重建页面
    isCache: 1
  })
  formVisible.value = true
}

function openEdit(row: MenuNode | SysMenu): void {
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    menuName: row.menuName,
    menuType: row.menuType,
    path: row.path ?? '',
    component: row.component ?? '',
    perms: row.perms ?? '',
    icon: row.icon ?? '',
    orderNum: row.orderNum ?? 0,
    visible: row.visible ?? 0,
    // MenuNode（树接口）不带 status，编辑时按"正常"兜底
    status: 'status' in row ? (row.status ?? 0) : 0,
    isCache: row.isCache ?? 1
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.id) {
      await updateMenu({ ...form })
    } else {
      await addMenu({ ...form })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: MenuNode): Promise<void> {
  await ElMessageBox.confirm(
    `确定删除「${row.menuName}」吗？若有下级菜单会一并删除。`,
    '提示',
    { type: 'warning' }
  )
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// 切换类型时清掉不适用字段，避免提交出"按钮带组件路径"这种脏数据
watch(
  () => form.menuType,
  (type) => {
    if (type === 3) {
      form.path = ''
      form.component = ''
      form.icon = ''
    } else if (type === 1) {
      form.component = ''
      form.perms = ''
    }
  }
)

onMounted(loadData)
</script>

<style scoped lang="scss">
.page {
  min-height: 100%;
}

.menu-icon-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);

  &__name {
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__empty {
    color: var(--el-text-color-placeholder);
  }
}

.menu-form__hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
