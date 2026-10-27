import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import router from '@/router'
import { usePermissionStore } from '@/stores/permission'

/** 页签上限。实测 50+ 页签时 DOM 节点 600+，切换明显卡顿，20 是主流取值 */
const TABS_MAX = Number(import.meta.env.VITE_TABS_MAX ?? 20) || 20

/** 这些路径不进页签栏 */
const EXCLUDE_PREFIX = ['/login', '/404', '/change-password']

/** 固定页签：不可关闭、不参与淘汰 */
const AFFIX_PATH = '/dashboard'

/** localStorage key 前缀，按用户隔离 */
const KEY_PREFIX = 'base_tabs_'
/** 其他用户的页签数据保留多久 */
const OTHER_USER_TTL = 7 * 24 * 60 * 60 * 1000

export interface TabItem {
  /** 唯一标识 = route.fullPath */
  key: string
  path: string
  fullPath: string
  title: string
  icon?: string
  /** 固定页签，不可关闭 */
  affix: boolean
  closable: boolean
  keepAlive: boolean
  /**
   * 组件实例版本号。关闭再打开 / 点刷新时自增，
   * 用来让 keep-alive 的 :key 变化 → 强制重建组件（否则会复用旧实例）。
   */
  seq: number
}

interface PersistedShape {
  v: number
  ts: number
  tabs: TabItem[]
  activeKey: string
  lastActiveAt: Record<string, number>
}

/**
 * 全局自增的版本号，保证 seq 单调递增。
 * 不能每个新页签都从 1 开始：那样「关掉 A 再打开 A」会拿到和上次一样的 key，
 * keep-alive 会把上次的缓存实例原样恢复出来，看起来就像没关掉。
 */
let seqCounter = 0

const storageKey = (userId: number): string => `${KEY_PREFIX}${userId}`

/**
 * 多页签。
 *
 * 持久化是**手写**的，没交给 pinia-plugin-persistedstate，原因是这个 store 有两个
 * 插件满足不了的要求：
 * 1. 存档按用户隔离（`base_tabs_{userId}`），而插件的 key 在定义时就定死了，
 *    做不到"登录谁就写谁的"——不做隔离的话换账号登录会看见上一个人的页签；
 * 2. 恢复时必须先校验：路由还在不在、当前账号有没有权、有没有超上限，还要把
 *    seq 计数器抬到最大值之上。插件的恢复发生在 store 首次实例化那一刻，
 *    那时动态路由还没生成，校验条件全是 false，结果会把页签一次性清空。
 *
 * 所以这里保留自己控制时机，但把散落在各 action 里的 persist() 调用收敛成一个
 * watch 自动触发，避免哪天加个新 action 忘了存。
 */
export const useTabsStore = defineStore(
  'tabs',
  () => {
    const tabs = ref<TabItem[]>([])
    const activeKey = ref('')
    /** LRU 时间戳：key -> Date.now() */
    const lastActiveAt = ref<Record<string, number>>({})
    const userId = ref(0)
    const hydrated = ref(false)

    /** 可关闭的页签数量，用于右键菜单置灰判断 */
    const closableCount = computed(() => tabs.value.filter((t) => !t.affix).length)

    /** 取某个页签当前的组件版本号，供 AppMain 拼 keep-alive 的 key */
    function seqOf(fullPath: string): number {
      return tabs.value.find((t) => t.fullPath === fullPath)?.seq ?? 0
    }

    /* ---------------- 持久化 ---------------- */

    function save(): void {
      // userId 为 0 说明还没登录或已清空：这时存下去会写到 base_tabs_0 这个垃圾 key
      if (!userId.value) return
      const payload: PersistedShape = {
        v: 1,
        ts: Date.now(),
        tabs: tabs.value,
        activeKey: activeKey.value,
        lastActiveAt: lastActiveAt.value
      }
      try {
        localStorage.setItem(storageKey(userId.value), JSON.stringify(payload))
      } catch {
        // localStorage 写满或隐私模式：忽略，不能因为存不下就阻断交互
      }
    }

    // 任何影响存档的字段变了就写一次，省得每个 action 末尾都要记得调
    watch([tabs, activeKey, lastActiveAt], save, { deep: true })

    function ensureAffixTab(target: TabItem[]): void {
      if (target.some((t) => t.affix)) return
      const permStore = usePermissionStore()
      const home = permStore.routes.find((r) => r.path === AFFIX_PATH)
      if (!home) return
      target.unshift({
        key: AFFIX_PATH,
        path: AFFIX_PATH,
        fullPath: AFFIX_PATH,
        title: (home.meta?.title as string) || '首页',
        affix: true,
        closable: false,
        keepAlive: true,
        seq: ++seqCounter
      })
    }

    /** 路由是否仍然可达：既要能匹配上，也要在当前用户已生成的动态路由里 */
    function isReachable(tab: TabItem): boolean {
      const permStore = usePermissionStore()
      if (!permStore.loaded) return false
      const resolved = router.resolve(tab.fullPath)
      // 命中 catch-all（/:pathMatch(.*)*）说明这条路已经没了
      if (!resolved.matched.length || resolved.matched.some((r) => r.path.includes('pathMatch'))) {
        return false
      }
      return permStore.routes.some((r) => r.path === tab.path)
    }

    /** 清掉 7 天没动过的其他用户页签数据，避免 localStorage 无限膨胀 */
    function clearExpiredOtherUsers(): void {
      const now = Date.now()
      const keys: string[] = []
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i)
        if (k && k.startsWith(KEY_PREFIX) && k !== storageKey(userId.value)) keys.push(k)
      }
      keys.forEach((k) => {
        const raw = localStorage.getItem(k)
        if (!raw) return
        try {
          const p = JSON.parse(raw) as PersistedShape
          if (!p?.ts || now - p.ts > OTHER_USER_TTL) localStorage.removeItem(k)
        } catch {
          localStorage.removeItem(k)
        }
      })
    }

    /**
     * 恢复当前用户的页签，并清掉失效项。
     * 只在登录后第一次进入 Layout 时调一次（见 router 的 afterEach）。
     */
    function hydrate(uid: number): void {
      if (hydrated.value && userId.value === uid) return
      userId.value = uid
      hydrated.value = true
      clearExpiredOtherUsers()

      const restoreDefault = (): void => {
        const initial: TabItem[] = []
        ensureAffixTab(initial)
        tabs.value = initial
        save()
      }

      const raw = localStorage.getItem(storageKey(uid))
      if (!raw) {
        // 首次登录没有历史数据，直接落到「固定页签 + 当前页」
        restoreDefault()
        return
      }

      let payload: PersistedShape
      try {
        payload = JSON.parse(raw)
      } catch {
        localStorage.removeItem(storageKey(uid))
        restoreDefault()
        return
      }
      if (!payload || payload.v !== 1 || !Array.isArray(payload.tabs)) {
        localStorage.removeItem(storageKey(uid))
        restoreDefault()
        return
      }

      const restoredTimes = payload.lastActiveAt ?? {}
      const timeOf = (t: TabItem): number => restoredTimes[t.key] ?? 0
      // ① 路由已不存在（菜单被删 / 改了 path）
      // ② 当前用户无权访问（换账号登录后路由表里没有）
      const valid = payload.tabs.filter(isReachable)
      // ③ 超上限：从最久未激活的开始丢，固定页签不参与
      const kept: TabItem[] = valid.filter((t) => t.affix)
      valid
        .filter((t) => !t.affix)
        .sort((a, b) => timeOf(a) - timeOf(b))
        .forEach((t) => {
          if (kept.length < TABS_MAX) kept.push(t)
        })
      kept.sort((a, b) => {
        if (a.affix !== b.affix) return a.affix ? -1 : 1
        return timeOf(a) - timeOf(b)
      })

      tabs.value = kept
      lastActiveAt.value = Object.fromEntries(kept.map((t) => [t.key, timeOf(t)]))
      ensureAffixTab(tabs.value)
      // 关键：把全局计数器抬到已恢复的最大值之上，
      // 否则新建页签的 seq 会和某个存活页签撞号，两个页签共用同一个缓存实例
      seqCounter = tabs.value.reduce((max, t) => Math.max(max, t.seq ?? 0), seqCounter)
      save()
    }

    function reset(): void {
      // 先删再清：下面把 userId 置 0 之后，save() 就找不到该删哪个 key 了
      if (userId.value) localStorage.removeItem(storageKey(userId.value))
      tabs.value = []
      activeKey.value = ''
      lastActiveAt.value = {}
      userId.value = 0
      hydrated.value = false
    }

    /* ---------------- 增删 ---------------- */

    function touch(key: string): void {
      lastActiveAt.value[key] = Date.now()
    }

    function addView(route: RouteLocationNormalizedLoaded): void {
      if (EXCLUDE_PREFIX.some((p) => route.path.startsWith(p))) return
      if (!route.name || route.meta?.hiddenInTabs) return

      const key = route.fullPath
      activeKey.value = key
      touch(key)

      const exist = tabs.value.find((t) => t.key === key)
      if (exist) {
        // 菜单改名/换图标后不用重启也能同步
        exist.title = (route.meta.title as string) || String(route.name)
        exist.icon = route.meta.icon as string | undefined
        return
      }

      if (tabs.value.length >= TABS_MAX) evictLru(key)

      tabs.value.push({
        key,
        path: route.path,
        fullPath: route.fullPath,
        title: (route.meta.title as string) || String(route.name),
        icon: route.meta.icon as string | undefined,
        affix: route.path === AFFIX_PATH,
        closable: route.path !== AFFIX_PATH,
        keepAlive: route.meta.keepAlive === true,
        seq: ++seqCounter
      })
    }

    /** 淘汰一个最久未激活、且非固定、且不是当前页的页签 */
    function evictLru(protectKey: string): void {
      const candidates = tabs.value.filter((t) => !t.affix && t.key !== protectKey)
      if (!candidates.length) return
      let oldest = candidates[0]
      for (const t of candidates) {
        if ((lastActiveAt.value[t.key] ?? 0) < (lastActiveAt.value[oldest.key] ?? 0)) oldest = t
      }
      tabs.value = tabs.value.filter((t) => t.key !== oldest.key)
      delete lastActiveAt.value[oldest.key]
      ElMessage.warning(`页签已达上限 ${TABS_MAX}，已自动关闭最久未访问的「${oldest.title}」`)
    }

    function delView(key: string): void {
      const idx = tabs.value.findIndex((t) => t.key === key)
      if (idx === -1) return
      const target = tabs.value[idx]
      if (target.affix) return
      tabs.value.splice(idx, 1)
      delete lastActiveAt.value[key]
      if (activeKey.value === key) {
        const next = tabs.value[idx] ?? tabs.value[idx - 1] ?? tabs.value.find((t) => t.affix)
        if (next) void router.push(next.fullPath)
      }
    }

    function delOthers(key: string): void {
      tabs.value = tabs.value.filter((t) => t.affix || t.key === key)
      Object.keys(lastActiveAt.value).forEach((k) => {
        if (!tabs.value.some((t) => t.key === k)) delete lastActiveAt.value[k]
      })
      if (activeKey.value !== key) void router.push(key)
    }

    function delLeft(key: string): void {
      const idx = tabs.value.findIndex((t) => t.key === key)
      if (idx <= 0) return
      tabs.value = tabs.value.filter((t, i) => t.affix || i >= idx)
    }

    function delRight(key: string): void {
      const idx = tabs.value.findIndex((t) => t.key === key)
      if (idx === -1) return
      tabs.value = tabs.value.filter((t, i) => t.affix || i <= idx)
    }

    function delAll(): void {
      tabs.value = tabs.value.filter((t) => t.affix)
      lastActiveAt.value = Object.fromEntries(
        tabs.value.map((t) => [t.key, lastActiveAt.value[t.key] ?? Date.now()])
      )
      const home = tabs.value.find((t) => t.affix)
      if (home) void router.push(home.fullPath)
    }

    /**
     * 刷新当前页：只自增该页签的 seq。
     * AppMain 用它拼组件 key，key 一变 Vue 就卸载旧实例、挂载新实例，
     * 效果等于重新加载，但 URL 不变、其他页签的缓存也不受影响。
     */
    function refresh(key: string): void {
      const tab = tabs.value.find((t) => t.key === key)
      if (!tab) return
      tab.seq = ++seqCounter
    }

    return {
      // 常量也放进来：模板里统一走 tabsStore.max，改配置时不用改页面
      max: TABS_MAX,
      tabs,
      activeKey,
      lastActiveAt,
      userId,
      hydrated,
      closableCount,
      seqOf,
      hydrate,
      reset,
      touch,
      addView,
      delView,
      delOthers,
      delLeft,
      delRight,
      delAll,
      refresh
    }
  },
  {
    // 存档按用户隔离且恢复时机有讲究，见文件头部注释：继续用手写的 save()/hydrate()
    persist: false
  }
)
