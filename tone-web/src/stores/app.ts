import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  DEFAULT_THEME_COLOR,
  applyDarkMode,
  applyPrimaryColor
} from '@/styles/theme'
import {
  DEFAULT_MASK_SCOPE,
  applyMaskScope,
  type MaskScope
} from '@/styles/mask'

/**
 * app store 在 localStorage 里的 key。
 *
 * main.ts 要在 pinia 挂载前就把主题打上去（否则首屏会闪一下默认蓝），
 * 那时拿不到 store 实例，只能直接读这个 key。两边共用同一个常量，
 * 就不会出现"存的是 A、读的是 B"这种对不上的情况。
 */
export const APP_PERSIST_KEY = 'base-app'

/** 迁移用：改用持久化插件之前，主题与遮罩是各自单独存的。读不到新 key 时回退一次 */
const LEGACY_KEYS = {
  themeColor: 'base_theme_color',
  dark: 'base_theme_dark',
  maskScope: 'base_mask_scope',
  collapsed: 'base_sidebar_collapsed'
}

export interface PersistedApp {
  collapsed: boolean
  fixedHeader: boolean
  themeColor: string
  dark: boolean
  maskScope: MaskScope
}

/**
 * 读取已持久化的 app 状态（纯函数，不依赖 pinia）。
 * 只在 main.ts 挂载前这一处使用；插件恢复会覆盖 store 的初始值，两者读的是同一份数据。
 */
export function readPersistedApp(): PersistedApp {
  const fallback: PersistedApp = {
    collapsed: false,
    fixedHeader: true,
    themeColor: DEFAULT_THEME_COLOR,
    dark: false,
    maskScope: DEFAULT_MASK_SCOPE
  }

  let saved: Partial<PersistedApp> | undefined
  try {
    const raw = localStorage.getItem(APP_PERSIST_KEY)
    if (raw) saved = JSON.parse(raw) as Partial<PersistedApp>
  } catch {
    // 存档损坏（手改过 / 版本不兼容）：当没存过，用默认值，不能因此白屏
    saved = undefined
  }

  // 没有新存档时回退旧 key，让老用户升级后主题不丢
  if (!saved?.themeColor) {
    const legacy = localStorage.getItem(LEGACY_KEYS.themeColor)
    if (legacy) fallback.themeColor = legacy
    fallback.dark = localStorage.getItem(LEGACY_KEYS.dark) === '1'
  }
  if (!saved?.maskScope) {
    const legacy = localStorage.getItem(LEGACY_KEYS.maskScope)
    if (legacy === 'page' || legacy === 'tab') fallback.maskScope = legacy
  }
  if (saved?.collapsed === undefined) {
    fallback.collapsed = localStorage.getItem(LEGACY_KEYS.collapsed) === '1'
  }

  return {
    collapsed: saved?.collapsed ?? fallback.collapsed,
    fixedHeader: saved?.fixedHeader ?? fallback.fixedHeader,
    themeColor: saved?.themeColor ?? fallback.themeColor,
    dark: saved?.dark ?? fallback.dark,
    maskScope: saved?.maskScope ?? fallback.maskScope
  }
}

/** 内容区遮罩的延迟句柄：见 beginPageLoading 的说明 */
let pageLoadingTimer: ReturnType<typeof setTimeout> | undefined

const stored = readPersistedApp()

/** 全局 UI 状态：与业务无关，只影响布局外观 */
export const useAppStore = defineStore(
  'app',
  () => {
    /** 侧边栏是否收起 */
    const collapsed = ref(stored.collapsed)
    /** 布局是否使用固定头部（内容区独立滚动） */
    const fixedHeader = ref(stored.fixedHeader)
    /** 主题色（十六进制），写入 Element Plus 的主色系列变量 */
    const themeColor = ref(stored.themeColor)
    /** 是否暗色模式 */
    const dark = ref(stored.dark)
    /**
     * 内容区遮罩：只在路由切换 / 页签切换这类"整页在换"的时刻打开，
     * 列表刷新仍由表格自己的 v-loading 负责，两者不重叠。
     */
    const pageLoading = ref(false)
    /**
     * 遮罩覆盖范围：
     * - tab（默认）：loading 与弹窗遮罩只盖当前页签那一块，顶栏/侧边栏/页签仍可操作
     * - page：盖住整个视口
     */
    const maskScope = ref<MaskScope>(stored.maskScope)

    function toggleCollapse(): void {
      collapsed.value = !collapsed.value
    }

    function setThemeColor(color: string): void {
      themeColor.value = color
      applyPrimaryColor(color, dark.value)
    }

    function setDark(next: boolean): void {
      dark.value = next
      applyDarkMode(next)
      // 必须重算：衍生色的混色方向取决于明暗，而写上去的是内联样式
      applyPrimaryColor(themeColor.value, next)
    }

    function setMaskScope(scope: MaskScope): void {
      maskScope.value = scope
      applyMaskScope(scope)
    }

    /**
     * 打开内容区遮罩。
     * 刻意延迟一小会儿：切到已缓存的页签是瞬时的，立刻打遮罩只会闪一下反而晃眼。
     * 超过阈值还没结束才说明真的在等数据（拉菜单、首次进页面）。
     */
    function beginPageLoading(): void {
      clearTimeout(pageLoadingTimer)
      pageLoadingTimer = setTimeout(() => {
        pageLoading.value = true
      }, 150)
    }

    function endPageLoading(): void {
      clearTimeout(pageLoadingTimer)
      pageLoading.value = false
    }

    return {
      collapsed,
      fixedHeader,
      themeColor,
      dark,
      pageLoading,
      maskScope,
      toggleCollapse,
      setThemeColor,
      setDark,
      setMaskScope,
      beginPageLoading,
      endPageLoading
    }
  },
  {
    /*
     * 只落这几个字段：pageLoading 是瞬时的，恢复成 true 会留下一个永远不消失的遮罩
     * （afterEach 一定会收掉，但刷新页面的那一刻没人收）。
     */
    persist: {
      key: APP_PERSIST_KEY,
      paths: ['collapsed', 'fixedHeader', 'themeColor', 'dark', 'maskScope'],
      /*
       * 注意：paths 只约束「写入」，恢复是整份 $patch，不过滤字段。
       * 正常流程下存档里不会有 pageLoading，但存档放在 localStorage 里
       * 是可以被手工改的，这里兜一次，免得改脏了就卡着一个遮罩。
       */
      afterRestore: (ctx) => {
        ctx.store.$state.pageLoading = false
      }
    }
  }
)
