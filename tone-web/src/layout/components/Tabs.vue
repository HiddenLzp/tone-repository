<template>
  <div class="tabs-bar">
    <el-scrollbar ref="scrollRef" class="tabs-bar__scroll" @wheel.passive="onWheel">
      <div class="tabs-bar__inner">
        <div
          v-for="t in tabsStore.tabs"
          :key="t.key"
          class="tabs-item"
          :class="{ 'is-active': t.key === tabsStore.activeKey }"
          @click="onSelect(t)"
          @contextmenu.prevent="openMenu($event, t)"
        >
          <el-icon v-if="t.affix" class="tabs-item__icon" :size="12"><HomeFilled /></el-icon>
          <span class="tabs-item__title">{{ t.title }}</span>
          <el-icon
            v-if="t.closable"
            class="tabs-item__close"
            :size="12"
            @click.stop="tabsStore.delView(t.key)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <div class="tabs-bar__actions">
      <el-tooltip content="刷新" placement="bottom">
        <el-icon class="tabs-bar__btn" :size="14" @click="tabsStore.refresh(tabsStore.activeKey)">
          <Refresh />
        </el-icon>
      </el-tooltip>
      <el-dropdown trigger="click" @command="onDropdown">
        <el-icon class="tabs-bar__btn" :size="14"><ArrowDown /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="others" :disabled="!tabsStore.closableCount">
              关闭其他
            </el-dropdown-item>
            <el-dropdown-item command="left" :disabled="!activeHasClosableLeft">
              关闭左侧
            </el-dropdown-item>
            <el-dropdown-item command="right" :disabled="!activeHasClosableRight">
              关闭右侧
            </el-dropdown-item>
            <el-dropdown-item command="all" divided :disabled="!tabsStore.closableCount">
              关闭全部
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 右键菜单 teleport 到 body，否则会被 el-scrollbar 的 overflow 裁掉 -->
    <teleport to="body">
      <ul
        v-if="menu.visible"
        class="tabs-contextmenu"
        :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
        @click="closeMenu"
      >
        <li @click="doRefresh">刷新</li>
        <li :class="{ 'is-disabled': !menu.closable }" @click="doClose">关闭</li>
        <li :class="{ 'is-disabled': !menuHasClosableLeft }" @click="doLeft">关闭左侧</li>
        <li :class="{ 'is-disabled': !menuHasClosableRight }" @click="doRight">关闭右侧</li>
        <li :class="{ 'is-disabled': !tabsStore.closableCount }" @click="doOthers">关闭其他</li>
        <li :class="{ 'is-disabled': !tabsStore.closableCount }" @click="doAll">关闭全部</li>
      </ul>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { ElScrollbar } from 'element-plus'
import { ArrowDown, Close, HomeFilled, Refresh } from '@element-plus/icons-vue'
import { useTabsStore } from '@/stores/tabs'
import type { TabItem } from '@/stores/tabs'

const router = useRouter()
const tabsStore = useTabsStore()
const scrollRef = ref<InstanceType<typeof ElScrollbar>>()

/** 竖向滚轮转成横向滚动：页签多的时候不用去拖那条细滚动条 */
function onWheel(e: WheelEvent): void {
  const wrap = scrollRef.value?.wrapRef
  if (!wrap) return
  wrap.scrollLeft += e.deltaY || e.deltaX
}

/** 把当前页签滚到可视区中间 */
function scrollToActive(): void {
  nextTick(() => {
    const wrap = scrollRef.value?.wrapRef
    const el = wrap?.querySelector<HTMLElement>('.tabs-item.is-active')
    if (!wrap || !el) return
    const left = el.offsetLeft - (wrap.clientWidth - el.offsetWidth) / 2
    wrap.scrollTo({ left: Math.max(0, left), behavior: 'smooth' })
  })
}
watch(() => tabsStore.activeKey, scrollToActive)
onMounted(scrollToActive)

/* ---------------- 右键菜单 ---------------- */

interface MenuState {
  visible: boolean
  x: number
  y: number
  key: string
  closable: boolean
}

const menu = reactive<MenuState>({ visible: false, x: 0, y: 0, key: '', closable: false })

function openMenu(e: MouseEvent, tab: TabItem): void {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  menu.key = tab.key
  menu.closable = tab.closable
  menu.x = rect.left
  menu.y = rect.bottom + 4
  menu.visible = true
}

function closeMenu(): void {
  menu.visible = false
}
onMounted(() => {
  document.addEventListener('click', closeMenu)
  window.addEventListener('scroll', closeMenu, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeMenu)
  window.removeEventListener('scroll', closeMenu, true)
})

const activeIndex = computed(() => tabsStore.tabs.findIndex((t) => t.key === tabsStore.activeKey))
const menuIndex = computed(() => tabsStore.tabs.findIndex((t) => t.key === menu.key))

const activeHasClosableLeft = computed(
  () => tabsStore.tabs.slice(0, activeIndex.value).some((t) => !t.affix)
)
const activeHasClosableRight = computed(() =>
  tabsStore.tabs.slice(activeIndex.value + 1).some((t) => !t.affix)
)
const menuHasClosableLeft = computed(() => {
  if (menuIndex.value <= 0) return false
  return tabsStore.tabs.slice(0, menuIndex.value).some((t) => !t.affix)
})
const menuHasClosableRight = computed(() => {
  if (menuIndex.value === -1) return false
  return tabsStore.tabs.slice(menuIndex.value + 1).some((t) => !t.affix)
})

function doRefresh(): void {
  tabsStore.refresh(menu.key)
}
function doClose(): void {
  if (menu.closable) tabsStore.delView(menu.key)
}
function doLeft(): void {
  if (menuHasClosableLeft.value) tabsStore.delLeft(menu.key)
}
function doRight(): void {
  if (menuHasClosableRight.value) tabsStore.delRight(menu.key)
}
function doOthers(): void {
  if (tabsStore.closableCount) tabsStore.delOthers(menu.key)
}
function doAll(): void {
  if (tabsStore.closableCount) tabsStore.delAll()
}

function onSelect(tab: TabItem): void {
  if (tab.key !== tabsStore.activeKey) void router.push(tab.fullPath)
}

function onDropdown(cmd: string): void {
  const key = tabsStore.activeKey
  if (cmd === 'others') tabsStore.delOthers(key)
  if (cmd === 'left') tabsStore.delLeft(key)
  if (cmd === 'right') tabsStore.delRight(key)
  if (cmd === 'all') tabsStore.delAll()
}
</script>

<style scoped lang="scss">
.tabs-bar {
  display: flex;
  align-items: center;
  height: 100%;
  /* 上下留 6px：页签项 28px + 12px = --base-tabs-height(40px)，
     紧贴着顶栏分隔线会显得压抑，两个数字要一起改 */
  padding: 6px 8px;
  user-select: none;

  &__scroll {
    flex: 1;
    min-width: 0;
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 4px;
    height: 100%;
    white-space: nowrap;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-left: 10px;
    flex-shrink: 0;
    border-left: 1px solid var(--el-border-color-lighter);
    /* 左侧那条分隔线只横跨页签的高度，不要顶到容器上下边 */
    height: 20px;
  }

  &__btn {
    cursor: pointer;
    color: var(--el-text-color-regular);
    &:hover {
      color: var(--el-color-primary);
    }
  }

  :deep(.el-scrollbar__wrap) {
    overflow-x: auto;
    overflow-y: hidden;
  }
  :deep(.el-scrollbar__bar.is-horizontal) {
    height: 2px;
    opacity: 0.3;
  }
}

.tabs-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 3px;
  cursor: pointer;
  transition: color 0.15s, background-color 0.15s;

  &:hover {
    color: var(--el-color-primary);
  }

  /*
   * 前景色不能写死 #fff：用户把主色改成浅黄/浅青时白字会完全看不见。
   * --base-primary-contrast 由 theme.ts 按主色亮度算出深色或白色。
   */
  &.is-active {
    color: var(--base-primary-contrast, #fff);
    background-color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }

  &__icon {
    opacity: 0.75;
  }

  &__close {
    border-radius: 50%;
    &:hover {
      background-color: var(--el-color-info-light-5);
      color: #fff;
    }
  }
}
</style>
