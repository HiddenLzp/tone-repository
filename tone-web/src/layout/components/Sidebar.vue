<template>
  <div class="sidebar">
    <div class="sidebar__logo">
      <span v-if="!appStore.collapsed">{{ title }}</span>
      <span v-else>BASE</span>
    </div>
    <el-scrollbar class="sidebar__scroll">
      <el-menu
        :default-active="activePath"
        :collapse="appStore.collapsed"
        :collapse-transition="false"
        unique-opened
        router
      >
        <SidebarItem v-for="menu in menus" :key="menu.id" :menu="menu" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SidebarItem from './SidebarItem.vue'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import type { MenuNode } from '@/types/api'

const appStore = useAppStore()
const permStore = usePermissionStore()
const route = useRoute()

const title = import.meta.env.VITE_APP_TITLE || '后台管理'

// hidden（visible=1）与按钮节点不进侧边栏；只有目录和菜单参与渲染
const menus = computed<MenuNode[]>(() => permStore.menus.filter((m) => m.menuType !== 3 && m.visible !== 1))

const activePath = computed(() => route.path)
</script>

<style scoped lang="scss">
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: var(--base-navbar-height);
    font-size: 16px;
    font-weight: 600;
    color: var(--el-color-primary);
    border-bottom: 1px solid var(--el-border-color-light);
    white-space: nowrap;
    overflow: hidden;
  }

  &__scroll {
    flex: 1;
    min-height: 0;
  }

  :deep(.el-menu) {
    border-right: none;
  }
}
</style>
