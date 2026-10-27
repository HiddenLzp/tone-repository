import { clearTokens } from '@/utils/auth'

/**
 * 会话收尾的统一出口：清 token、清内存态、卸载动态路由。
 *
 * 为什么要单独抽出来：以前「被强退 / 主动登出 / 改密后重登」三处各写一遍清理逻辑，
 * 漏掉任意一项就会出怪事。最典型的一次是只清了 localStorage 没清 Pinia——
 * 路由守卫读的是 store 里的 accessToken，于是守卫认为你仍然登录着，
 * 把 /login 又弹回首页，用户看到的就是「提示弹了，但页面纹丝不动」。
 *
 * 这里一律用动态 import：本模块会被 request.ts 调用，
 * 而 store → api → request 是一条链，静态引入会成环。
 */
export async function clearSession(): Promise<void> {
  // 先清一次：下面几个动态 import 期间可能还有请求在飞，别让它们带上旧 token
  clearTokens()

  const [{ useUserStore }, { usePermissionStore }, { useTabsStore }, { removeDynamicRoutes }] =
    await Promise.all([
      import('@/stores/user'),
      import('@/stores/permission'),
      import('@/stores/tabs'),
      import('@/router')
    ])

  useUserStore().reset()

  // 顺序不能反：要卸载的是**当前这批**动态路由，
  // permStore.reset() 一调用 routes 就空了，之后再清就无从下手
  removeDynamicRoutes()
  usePermissionStore().reset()

  // 页签按 userId 存在 localStorage，不清的话下一个账号会看到上一个人的页签
  useTabsStore().reset()
}
