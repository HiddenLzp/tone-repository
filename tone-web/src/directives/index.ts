import type { App, Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。
 *
 * 用法：<el-button v-hasPerm="'system:user:add'">新增</el-button>
 *       <el-button v-hasRole="['SUPER_ADMIN']">仅超管可见</el-button>
 *
 * 说明：前端隐藏只是体验优化，真正的拦截在后端接口上（@RequiresPermissions）。
 * 指令在无权限时直接移除 DOM 节点，而不是 display:none——后者仍可被调试工具改回来。
 */
function createChecker(getter: (perms: string[], roles: string[], value: unknown) => boolean): Directive {
  return {
    mounted(el, binding) {
      const userStore = useUserStore()
      // 超管不受限制
      if (userStore.isSuperAdmin) return
      if (!getter(userStore.perms, userStore.roles, binding.value)) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}

export function hasPerm(perm: string | string[]): boolean {
  const userStore = useUserStore()
  if (userStore.isSuperAdmin) return true
  const list = Array.isArray(perm) ? perm : [perm]
  return list.some((p) => userStore.perms.includes(p))
}

export function hasRole(role: string | string[]): boolean {
  const userStore = useUserStore()
  if (userStore.isSuperAdmin) return true
  const list = Array.isArray(role) ? role : [role]
  return list.some((r) => userStore.roles.includes(r))
}

export function setupDirectives(app: App): void {
  app.directive('hasPerm', createChecker((perms, _roles, value) => {
    const list = Array.isArray(value) ? value : [String(value)]
    return list.some((p) => perms.includes(p))
  }))
  app.directive('hasRole', createChecker((_perms, roles, value) => {
    const list = Array.isArray(value) ? value : [String(value)]
    return list.some((r) => roles.includes(r))
  }))
}
