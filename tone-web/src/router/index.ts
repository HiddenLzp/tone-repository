import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { useAppStore } from '@/stores/app'
import { BizError, CODE_PASSWORD_MUST_CHANGE } from '@/utils/request'
import { clearSession } from '@/utils/session'

NProgress.configure({ showSpinner: false })

/**
 * 静态路由：不依赖权限，任何人都可访问。
 * 业务页面全部由后端菜单动态生成，见 stores/permission.ts。
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/change-password/index.vue'),
    meta: { title: '修改密码', hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true }
  },
  {
    // 动态路由都挂在这个父路由下，由 BasicLayout 提供外框
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: []
  }
]

/** 兜底路由，必须在动态路由之后注册，否则会先把未匹配的地址吃掉 */
const notFoundRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  // 取个名字才能在切换账号时精确卸载（removeRoute 只认 name）
  name: 'PathMatch',
  redirect: '/404'
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 卸载已注册的动态路由。
 *
 * 只把 permStore.loaded 置回 false 是不够的：matcher 里的记录还在，
 * 换一个权限更小的账号登录后，直接输 URL 仍能命中上一个账号的页面。
 * 接口层还有权限拦截，但页面会被渲染出来，属于越权可见。
 */
export function removeDynamicRoutes(): void {
  usePermissionStore().routes.forEach((route) => {
    if (route.name) {
      router.removeRoute(route.name)
    }
  })
  // 通配兜底排在动态路由之后，留着会挡住下次注册的路由匹配
  if (router.hasRoute('PathMatch')) {
    router.removeRoute('PathMatch')
  }
}

/** 登录后落地页：优先首页，没有权限就落到第一个有权限的页面 */
function homePath(): string {
  const permStore = usePermissionStore()
  const first = permStore.routes.find((r) => !r.meta?.hidden && r.path !== '/')
  if (permStore.routes.some((r) => r.path === '/dashboard')) {
    return '/dashboard'
  }
  return (first?.path as string) || '/404'
}

router.beforeEach(async (to) => {
  NProgress.start()
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  const appStore = useAppStore()
  // 内容区遮罩：路由切换期间（含拉菜单的等待）盖住内容区，由 afterEach 收掉。
  // 只管"整页在换"的时刻，列表刷新交给表格自己的 v-loading。
  appStore.beginPageLoading()
  const token = userStore.accessToken

  // 1. 未登录：登录页放行，其余带 redirect 回登录页
  if (!token) {
    if (to.path === '/login') return true
    return { path: '/login', query: to.path === '/' ? {} : { redirect: to.fullPath } }
  }

  // 2. 刷新页面后内存里的用户信息没了，先补回来
  if (!userStore.userInfo) {
    try {
      await userStore.loadUserInfo()
    } catch {
      // 拉取失败（token 失效/被强退）→ 清状态并回登录页
      await clearSession()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 3. 首次登录强制改密：改密完成前只停留在改密页。
  //    /change-password 是静态路由，渲染它不需要任何菜单数据，必须在这里直接放行；
  //    掉到第 4 步去拉菜单必然 403——后端改密态只放行
  //    /auth/logout、/auth/change-password、/auth/info 三个接口。
  if (userStore.mustChangePassword) {
    return to.path === '/change-password' ? true : { path: '/change-password', replace: true }
  }

  // 4. 动态路由：必须排在 /login、/ 的落地判定之前，
  //    否则 homePath() 读到空路由表只能返回 /404
  if (!permStore.loaded) {
    try {
      const routes = await permStore.generateRoutes()
      routes.forEach((route) => router.addRoute('Layout', route))
      router.addRoute(notFoundRoute)
      // replace: true 重新走一次导航，此时动态路由已就绪
      return { ...to, replace: true }
    } catch (e) {
      // 20010 = token 有效但被隔离在改密态，此时清 token 等于销毁有效会话。
      // /auth/info 在改密白名单里一定能成功，重新拉一次作为权威依据，避免来回横跳。
      if (e instanceof BizError && e.code === CODE_PASSWORD_MUST_CHANGE) {
        await userStore.loadUserInfo().catch(() => undefined)
        if (userStore.mustChangePassword) {
          return { path: '/change-password', replace: true }
        }
      }
      await clearSession()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 5. 已登录还去登录页：直接送回首页，避免"登录了还能看到登录页"的割裂感
  if (to.path === '/login') {
    return { path: homePath(), replace: true }
  }

  // 6. 访问根路径时落到真正的首页
  if (to.path === '/') {
    return { path: homePath(), replace: true }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta?.title as string | undefined
  document.title = title ? `${title} - ${import.meta.env.VITE_APP_TITLE}` : import.meta.env.VITE_APP_TITLE
  NProgress.done()
  // 与 beforeEach 里打开的内容区遮罩配对
  useAppStore().endPageLoading()

  /*
   * 页签只在 afterEach 里加：
   * beforeEach 里有 `return { ...to, replace: true }` 的重入，放那边会重复添加；
   * 而 afterEach 只在导航真正确认后触发一次。
   */
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  const tabsStore = useTabsStore()
  if (userStore.userId && permStore.loaded) {
    if (!tabsStore.hydrated) tabsStore.hydrate(userStore.userId)
    tabsStore.addView(to)
  }
})

export default router
