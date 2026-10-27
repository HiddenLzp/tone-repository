import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { getUserRoutes } from '@/api/system'
import type { MenuNode } from '@/types/api'

/**
 * views 下所有 .vue 的映射表。
 * 后端菜单表里的 component 字段（如 system/user/index）在这里查到真实组件，
 * 前端不再维护一份"菜单 → 组件"的硬编码表，加页面只要建文件 + 配菜单即可。
 */
const viewModules = import.meta.glob('../views/**/*.vue')

function resolveView(component?: string) {
  if (!component) return undefined
  const normalized = component.replace(/^\/+/, '').replace(/\.vue$/, '')
  return viewModules[`../views/${normalized}.vue`]
}

/**
 * 菜单树 → 路由表。
 *
 * 这里刻意做成"扁平"结构（目录节点不参与路由）：
 * 后端菜单里的父子关系已经用绝对路径表达（/system、/system/user），
 * 再套一层嵌套路由只会让面包屑和缓存逻辑变复杂，收益却为零。
 */
function toRoutes(nodes: MenuNode[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const node of nodes) {
    // 按钮（menuType=3）不是页面；目录（menuType=1）仅用于侧边栏分组
    if (node.menuType === 3 || !node.path) {
      continue
    }
    if (node.menuType === 2) {
      const component = resolveView(node.component)
      if (!component) {
        console.warn(`[router] 菜单 ${node.menuName} 的组件 ${node.component} 未找到，已跳过`)
        continue
      }
      routes.push({
        path: node.path,
        // 用 path 当 name：全局唯一，且 keep-alive 的 include 能直接引用
        name: node.path,
        component,
        meta: {
          title: node.menuName,
          icon: node.icon,
          // visible=1 的菜单（如个人中心）不在侧边栏展示，但仍可路由
          hidden: node.visible === 1,
          /*
           * 缓存默认开，只有显式标了 0 的页面才不缓存。
           * 早期写法是 `isCache === 1`，配上 is_cache 列 DEFAULT 0 的默认值，
           * 结果全站页面都进不了 keep-alive —— 页签一换组件就销毁重建，
           * 筛选条件、滚动位置、甚至开着的弹框全丢，多页签等于白做。
           * 缺字段（老数据、菜单表单没提交这个字段）也按缓存处理：
           * 缓存是"页面该有的默认待遇"，不缓存才需要显式声明。
           */
          keepAlive: node.isCache !== 0,
          menuId: node.id
        }
      })
    }
    if (node.children?.length) {
      routes.push(...toRoutes(node.children))
    }
  }
  return routes
}

/**
 * 动态路由与菜单。
 *
 * 刻意**不持久化**：路由表是按当前用户的权限生成的，把它缓存下来，
 * 换一个权限更小的账号、或管理员刚收回某个菜单时，本地这份"过期路由"
 * 仍然能被 URL 直接命中（页面会渲染出来，只是接口层报错），属于越权可见。
 * 每次登录重新拉一次，多一个请求的代价换这个安全性，值。
 */
export const usePermissionStore = defineStore(
  'permission',
  () => {
    /** 原始菜单树，侧边栏直接渲染它 */
    const menus = ref<MenuNode[]>([])
    /** 由菜单树生成的动态路由 */
    const routes = ref<RouteRecordRaw[]>([])
    const loaded = ref(false)

    async function generateRoutes(): Promise<RouteRecordRaw[]> {
      const tree = await getUserRoutes()
      menus.value = tree
      routes.value = toRoutes(tree)
      loaded.value = true
      return routes.value
    }

    function reset(): void {
      menus.value = []
      routes.value = []
      loaded.value = false
    }

    return { menus, routes, loaded, generateRoutes, reset }
  },
  {
    persist: false
  }
)
