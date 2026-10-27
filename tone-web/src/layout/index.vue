<template>
  <div class="base-layout" :class="{ 'base-layout--collapsed': appStore.collapsed }">
    <el-container class="base-layout__container">
      <el-aside class="base-layout__aside" :width="asideWidth">
        <Sidebar />
      </el-aside>
      <el-container class="base-layout__main">
        <el-header class="base-layout__header" :height="navbarHeight">
          <Navbar />
        </el-header>
        <!-- 页签栏单独一层，不放进 el-header，避免动 Navbar 内部的 flex 布局 -->
        <div class="base-layout__tabs">
          <Tabs />
        </div>
        <!--
          内容级 loading：遮罩范围恰好是页签栏以下这一块。
          不用全屏遮罩——那会连侧边栏、顶栏、页签一起盖住，多页签就没法并行操作了；
          而且它和无感刷新的请求重放会打架（刷新期间遮罩一开一合地闪）。
          列表刷新仍是表格自己的 v-loading，两者不重叠。
        -->
        <el-main
          ref="contentRef"
          v-loading="appStore.pageLoading"
          class="base-layout__content"
          element-loading-text="加载中"
        >
          <AppMain />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import Tabs from './components/Tabs.vue'
import AppMain from './components/AppMain.vue'
import { useAppStore } from '@/stores/app'
import { applyMaskInset } from '@/styles/mask'

const appStore = useAppStore()

const asideWidth = computed(() =>
  appStore.collapsed ? 'var(--base-sidebar-collapsed-width)' : 'var(--base-sidebar-width)'
)
const navbarHeight = 'var(--base-navbar-height)'

/* ---------------- 遮罩范围（tab 模式） ---------------- */

const contentRef = ref<ComponentPublicInstance | null>(null)
let resizeObserver: ResizeObserver | undefined

/**
 * 把内容区在视口里的位置喂给 CSS，tab 模式的遮罩就贴着这块矩形。
 * 触发时机：挂载后、侧边栏收放（ResizeObserver 会连续回调，覆盖 0.2s 的过渡动画）、
 * 浏览器窗口缩放。
 */
function syncMaskInset(): void {
  const el = contentRef.value?.$el as HTMLElement | undefined
  if (!el) return
  const rect = el.getBoundingClientRect()
  applyMaskInset(
    rect.top,
    window.innerWidth - rect.right,
    window.innerHeight - rect.bottom,
    rect.left
  )
}

onMounted(() => {
  syncMaskInset()
  const el = contentRef.value?.$el as HTMLElement | undefined
  if (el) {
    resizeObserver = new ResizeObserver(syncMaskInset)
    resizeObserver.observe(el)
  }
  window.addEventListener('resize', syncMaskInset)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', syncMaskInset)
})
</script>

<style scoped lang="scss">
.base-layout,
.base-layout__container {
  height: 100%;
}

/* 侧边栏与主区各自独立滚动，页面切换时不会互相影响滚动位置 */
.base-layout__aside {
  overflow: hidden;
  background-color: var(--el-menu-bg-color, #fff);
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.2s;
}

.base-layout__main {
  height: 100%;
  min-width: 0;
}

.base-layout__header {
  padding: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
}

.base-layout__tabs {
  height: var(--base-tabs-height);
  overflow: hidden;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.base-layout__content {
  padding: 12px;
  /* 关键：内容区自己滚，body 不滚，否则固定头部会失效 */
  height: calc(100% - var(--base-navbar-height) - var(--base-tabs-height));
  overflow: auto;
  background-color: var(--el-bg-color-page);
}
</style>
