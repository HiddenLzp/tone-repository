<template>
  <el-breadcrumb separator="/" class="breadcrumb">
    <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
    <el-breadcrumb-item v-for="item in items" :key="item.id">
      {{ item.menuName }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import type { MenuNode } from '@/types/api'

/**
 * 集中式面包屑。
 *
 * 不从 route.matched 推导：动态路由是扁平的（目录节点不参与路由），matched 拿不到完整层级。
 * 改成拿当前 path 去菜单树里反查父链，层级与侧边栏天然一致。
 */
const route = useRoute()
const permStore = usePermissionStore()

function findPath(nodes: MenuNode[], path: string, trail: MenuNode[] = []): MenuNode[] | null {
  for (const node of nodes) {
    const next = [...trail, node]
    if (node.path && node.path === path && node.menuType === 2) {
      return next
    }
    if (node.children?.length) {
      const found = findPath(node.children, path, next)
      if (found) return found
    }
  }
  return null
}

const items = computed<MenuNode[]>(() => {
  const full = findPath(permStore.menus, route.path)
  if (!full) return []
  // 去掉最后一级自己之外的"目录"已在链里，这里只去掉末尾与当前页重复的首页项
  return full.filter((n) => n.path !== '/dashboard')
})
</script>

<style scoped lang="scss">
.breadcrumb {
  font-size: 13px;
  line-height: 1;
}
</style>
