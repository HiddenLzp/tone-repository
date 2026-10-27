import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/auth'
import type { LoginRequest, UserInfo } from '@/types/api'

/**
 * 用户状态：token + 个人信息 + 权限标识。
 *
 * 权限标识（perms）在这里而不是单独存一份：按钮级控制全部走 hasPerm，
 * 来源唯一，避免菜单树和权限列表两份数据不一致。
 *
 * 刻意**不持久化**：
 * - token 由 utils/auth.ts 单独管，axios 拦截器要在 store 之外同步读它，
 *   再让插件持久化一份，就会出现两个来源，清一个漏一个；
 * - userInfo 里的角色与权限是接口结果，刷新后必须重新拉（管理员刚改了你的权限，
 *   拿本地缓存继续跑就是越权可见）。路由守卫里 loadUserInfo 就是干这个的。
 */
export const useUserStore = defineStore(
  'user',
  () => {
    const accessToken = ref(getAccessToken())
    const refreshToken = ref(getRefreshToken())
    const userInfo = ref<UserInfo | null>(null)

    const userId = computed(() => userInfo.value?.userId ?? 0)
    const username = computed(() => userInfo.value?.username ?? '')
    const nickname = computed(() => userInfo.value?.nickname ?? userInfo.value?.username ?? '')
    const avatar = computed(() => userInfo.value?.avatar ?? '')
    const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
    const perms = computed<string[]>(() => userInfo.value?.perms ?? [])
    const isSuperAdmin = computed(() => userInfo.value?.superAdmin === true)
    const mustChangePassword = computed(() => userInfo.value?.mustChangePassword === true)
    const isLogin = computed(() => !!accessToken.value)

    async function login(data: LoginRequest): Promise<void> {
      const vo = await loginApi(data)
      accessToken.value = vo.accessToken
      refreshToken.value = vo.refreshToken
      setTokens(vo.accessToken, vo.refreshToken)
    }

    /** 拉取个人信息（权限标识在这里拿到），返回是否需要强制改密 */
    async function loadUserInfo(): Promise<boolean> {
      const info = await getUserInfo()
      userInfo.value = info
      return info.mustChangePassword === true
    }

    /**
     * 用后端返回的最新用户信息覆盖本地（个人中心改资料后调用）。
     * 不做局部字段赋值：权限、角色、部门这些也在同一份 userInfo 里，
     * 整份换掉才能保证右上角、昵称、头像到处都是一致的。
     */
    function setUserInfo(info: UserInfo): void {
      userInfo.value = info
    }

    /** 只换头像：上传接口只返回地址，没必要为它再拉一次 /auth/info */
    function setAvatar(url: string): void {
      if (userInfo.value) {
        userInfo.value.avatar = url
      }
    }

    async function logout(): Promise<void> {
      try {
        // 先调后端：让 refreshToken 与令牌版本失效，否则只清本地等于"假登出"
        await logoutApi()
      } catch {
        // 后端登出失败（token 已过期等）也要清本地，不能把用户卡在系统里
      } finally {
        reset()
      }
    }

    function reset(): void {
      accessToken.value = ''
      refreshToken.value = ''
      userInfo.value = null
      clearTokens()
    }

    return {
      accessToken,
      refreshToken,
      userInfo,
      userId,
      username,
      nickname,
      avatar,
      roles,
      perms,
      isSuperAdmin,
      mustChangePassword,
      isLogin,
      login,
      loadUserInfo,
      setUserInfo,
      setAvatar,
      logout,
      reset
    }
  },
  {
    persist: false
  }
)
