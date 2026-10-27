/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_PORT: string
  readonly VITE_API_PREFIX: string
  readonly VITE_PROXY_TARGET: string
  /** 多页签上限，超出后按 LRU 自动关闭最久未访问的页签 */
  readonly VITE_TABS_MAX: string
  /** 遮罩默认范围：tab = 只盖当前页签那一块，page = 盖住整个视口 */
  readonly VITE_MASK_SCOPE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
