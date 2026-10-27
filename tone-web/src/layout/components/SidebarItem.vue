<template>
  <!-- 目录：有子菜单时渲染成可展开分组 -->
  <el-sub-menu v-if="hasChildren" :index="String(menu.id)">
    <template #title>
      <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
      <span>{{ menu.menuName }}</span>
    </template>
    <SidebarItem v-for="child in children" :key="child.id" :menu="child" />
  </el-sub-menu>

  <!-- 菜单：外链用 a 标签新开，内链走 router -->
  <el-menu-item v-else-if="!hidden" :index="menu.path || String(menu.id)">
    <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
    <template #title>{{ menu.menuName }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MenuNode } from '@/types/api'

const props = defineProps<{ menu: MenuNode }>()

// 递归渲染子菜单时只保留目录/菜单，按钮节点（menuType=3）不属于导航
const children = computed<MenuNode[]>(() =>
  (props.menu.children || []).filter((c) => c.menuType !== 3 && c.visible !== 1)
)

const hasChildren = computed(() => children.value.length > 0)
const hidden = computed(() => props.menu.visible === 1)
</script>
