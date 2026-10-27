<template>
  <router-view v-slot="{ Component, route }">
    <!--
      key 里带上页签的 seq：
      - 关掉页签再打开 → seq 自增 → key 变 → 重新挂载（不会复用上次残留的状态）
      - 点「刷新」→ 只自增当前页签的 seq → 只重建这一页，兄弟页签缓存不受影响
      keep-alive 的 :max 只作内存兜底，真正的生命周期由页签控制。
    -->
    <keep-alive :max="max">
      <component v-if="route.meta?.keepAlive" :is="Component" :key="cacheKey(route)" />
    </keep-alive>
    <component v-if="!route.meta?.keepAlive" :is="Component" :key="cacheKey(route)" />
  </router-view>
</template>

<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { watch } from 'vue'
import { useTabsStore } from '@/stores/tabs'
import { hideOverlaysOf, showOverlaysOf } from '@/utils/page-overlay'

const tabsStore = useTabsStore()
const max = tabsStore.max

function cacheKey(route: RouteLocationNormalizedLoaded): string {
  return `${route.fullPath}#${tabsStore.seqOf(route.fullPath)}`
}

/*
 * 切页签时把上一个页签「飘在 body 上」的弹框藏起来，切回来再放出来。
 * 就地渲染的弹框（没写 append-to-body）不经过这里，它们随 keep-alive 自动显隐。
 * 注意顺序：先藏旧的，再放新的，否则来回切时会把刚放出来的又藏回去。
 */
watch(
  () => tabsStore.activeKey,
  (to, from) => {
    if (from && from !== to) hideOverlaysOf(from)
    if (to) showOverlaysOf(to)
  }
)
</script>
