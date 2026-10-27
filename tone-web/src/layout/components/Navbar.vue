<template>
  <div class="navbar">
    <div class="navbar__left">
      <el-icon class="navbar__toggle" :size="20" @click="appStore.toggleCollapse()">
        <component :is="appStore.collapsed ? 'Expand' : 'Fold'" />
      </el-icon>
      <BaseBreadcrumb />
    </div>

    <div class="navbar__right">
      <el-tooltip content="刷新当前页" placement="bottom">
        <el-icon class="navbar__icon" :size="18" @click="refresh"><component :is="Refresh" /></el-icon>
      </el-tooltip>

      <!-- 主题：预设色 + 自定义取色 + 暗色开关 -->
      <el-dropdown trigger="click" placement="bottom-end" popper-class="theme-dropdown">
        <el-icon class="navbar__icon" :size="18"><component :is="Brush" /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <div class="theme-panel">
              <div class="theme-panel__title">主题色</div>
              <div class="theme-panel__presets">
                <button
                  v-for="p in THEME_PRESETS"
                  :key="p.color"
                  type="button"
                  class="theme-swatch"
                  :class="{ 'is-active': appStore.themeColor.toLowerCase() === p.color }"
                  :style="{ background: p.color }"
                  :title="p.name"
                  @click="appStore.setThemeColor(p.color)"
                >
                  <el-icon v-if="appStore.themeColor.toLowerCase() === p.color" :size="14">
                    <component :is="Check" />
                  </el-icon>
                </button>
              </div>
              <div class="theme-panel__custom">
                <span class="theme-panel__label">自定义</span>
                <el-color-picker
                  :model-value="appStore.themeColor"
                  :predefine="THEME_PRESETS.map((p) => p.color)"
                  size="small"
                  @change="(v: string | null) => v && appStore.setThemeColor(v)"
                />
              </div>
              <div class="theme-panel__row">
                <div>
                  <div class="theme-panel__label">遮罩范围</div>
                  <div class="theme-panel__hint">加载与弹窗遮住的区域</div>
                </div>
                <el-radio-group
                  :model-value="appStore.maskScope"
                  size="small"
                  @change="onMaskScopeChange"
                >
                  <el-radio-button value="tab">选项卡</el-radio-button>
                  <el-radio-button value="page">全页面</el-radio-button>
                </el-radio-group>
              </div>
              <div class="theme-panel__dark">
                <span class="theme-panel__label">暗色模式</span>
                <el-switch :model-value="appStore.dark" @change="appStore.setDark($event as boolean)" />
              </div>
            </div>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown trigger="click" @command="handleCommand">
        <span class="navbar__user">
          <el-avatar :size="26" :src="userStore.avatar || undefined">
            {{ userStore.nickname.charAt(0) }}
          </el-avatar>
          <span class="navbar__name">{{ userStore.nickname }}</span>
          <el-icon><component :is="ArrowDown" /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><component :is="UserIcon" /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><component :is="Lock" /></el-icon>修改密码
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><component :is="SwitchButton" /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  Refresh,
  ArrowDown,
  Lock,
  SwitchButton,
  Brush,
  Check,
  User as UserIcon
} from '@element-plus/icons-vue'
import BaseBreadcrumb from './BaseBreadcrumb.vue'
import { THEME_PRESETS } from '@/styles/theme'
import type { MaskScope } from '@/styles/mask'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useTabsStore } from '@/stores/tabs'
import { clearSession } from '@/utils/session'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const tabsStore = useTabsStore()

/**
 * 刷新当前页：只重建当前页签对应的组件实例。
 * 不再用 window.location.reload() —— 那会把整个应用连同其他页签一起重启，
 * 有了多页签之后语义就不对了。
 */
function refresh(): void {
  tabsStore.refresh(tabsStore.activeKey)
}

/** 遮罩范围：radio-group 回调是宽类型，这里收敛回 MaskScope */
function onMaskScopeChange(value: string | number | boolean): void {
  const scope: MaskScope = value === 'page' ? 'page' : 'tab'
  appStore.setMaskScope(scope)
}

async function handleCommand(command: string): Promise<void> {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'password') {
    router.push('/change-password')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await userStore.logout()
    // 与「被强退」走同一套收尾：清 store、卸载动态路由、清页签
    await clearSession()
    ElMessage.success('已退出登录')
    router.replace({ path: '/login' })
  }
}
</script>

<style scoped lang="scss">
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 12px;

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__toggle,
  &__icon {
    cursor: pointer;
    color: var(--el-text-color-regular);
    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    outline: none;
  }

  &__name {
    font-size: 14px;
    color: var(--el-text-color-regular);
  }
}

/*
 * 主题面板放在 dropdown 里（teleport 到 body），元素仍带 scoped 标记，
 * 所以这里照常写 scoped 样式即可生效。
 */
.theme-panel {
  width: 236px;
  padding: 6px 12px 10px;

  &__title {
    margin-bottom: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__presets {
    display: flex;
    gap: 8px;
    margin-bottom: 10px;
  }

  &__custom,
  &__dark,
  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 8px;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  &__label {
    font-size: 12px;
    color: var(--el-text-color-regular);
  }

  &__hint {
    margin-top: 2px;
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.theme-swatch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  /* 色块上的对勾要看得清，跟着主色深浅自动切黑白 */
  color: var(--base-primary-contrast, #fff);
  cursor: pointer;
  transition: transform 0.15s;

  &:hover {
    transform: scale(1.08);
  }

  &.is-active {
    box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
  }
}
</style>
