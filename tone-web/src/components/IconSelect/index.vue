<template>
  <div class="icon-select">
    <el-popover
      v-model:visible="visible"
      trigger="click"
      placement="bottom-start"
      :width="400"
      :disabled="disabled"
      popper-class="icon-select-popper"
      @show="onShow"
    >
      <template #reference>
        <!--
          触发器做成仿输入框的样子，而不是直接用 el-input：
          el-input 的内容区塞不下"图标 + 名称"，而且点它的任意位置都会聚焦输入框、
          弹出的是键盘而不是图标面板。这里自己画一个，点击整块都能打开面板。
        -->
        <div
          class="icon-select__field"
          :class="{ 'is-disabled': disabled, 'is-placeholder': !modelValue }"
        >
          <el-icon v-if="currentIcon" class="icon-select__icon" :size="16">
            <component :is="currentIcon" />
          </el-icon>
          <span class="icon-select__label">{{ modelValue || placeholder }}</span>
          <el-icon
            v-if="clearable && modelValue && !disabled"
            class="icon-select__clear"
            :size="14"
            @click.stop="clear"
          >
            <CircleClose />
          </el-icon>
          <el-icon v-else class="icon-select__arrow" :size="14"><ArrowDown /></el-icon>
        </div>
      </template>

      <div class="icon-select__panel">
        <el-input v-model="keyword" size="small" clearable placeholder="搜索：User 或 用户">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="icon-select__grid">
          <div
            v-for="item in matched"
            :key="item.name"
            class="icon-select__item"
            :class="{ 'is-active': item.name === modelValue }"
            :title="item.name"
            @click="select(item.name)"
          >
            <el-icon :size="18"><component :is="item.comp" /></el-icon>
          </div>
        </div>

        <div v-if="!matched.length" class="icon-select__empty">没有匹配的图标</div>

        <div class="icon-select__footer">
          <span>共 {{ matched.length }} 个{{ keyword ? `（全部 ${ICONS.length}）` : '' }}</span>
          <el-button link type="primary" size="small" :disabled="!modelValue" @click="clear">
            清除已选
          </el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, CircleClose, Search } from '@element-plus/icons-vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { ICON_KEYWORDS } from './keywords'

/**
 * 图标选择器。
 *
 * 用法：<IconSelect v-model="form.icon" />
 * 进出都是**图标名字符串**（如 "User"），和后端 sys_menu.icon 存的东西一致，
 * 侧边栏那边仍是 <component :is="menu.icon" />，不用改渲染方式。
 *
 * 图标源直接用 @element-plus/icons-vue 的导出，不在本地维护一份名单——
 * 库升级加了新图标，这边自动就能选到，不会出现"库里有但选不到"。
 */
const props = withDefaults(
  defineProps<{
    /** 图标名，如 User */
    modelValue?: string
    placeholder?: string
    disabled?: boolean
    /** 是否显示清除按钮 */
    clearable?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: '点击选择图标',
    disabled: false,
    clearable: true
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const visible = ref(false)
const keyword = ref('')

/** 全部图标，按名称排序。组件对象在这里一次性取好，避免模板里反复查表 */
const ICONS: { name: string; comp: Component }[] = Object.entries(
  ElementPlusIcons as unknown as Record<string, Component>
)
  .map(([name, comp]) => ({ name, comp }))
  .sort((a, b) => a.name.localeCompare(b.name))

const currentIcon = computed<Component | string>(
  () => ICONS.find((i) => i.name === props.modelValue)?.comp ?? props.modelValue ?? ''
)

const matched = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return ICONS
  return ICONS.filter(({ name }) => {
    if (name.toLowerCase().includes(kw)) return true
    // 中文：命中别名里任意一个词即可，如"用户"能搜到 User
    const alias = ICON_KEYWORDS[name]
    return !!alias && alias.split(/\s+/).some((w) => w.includes(kw))
  })
})

function select(name: string): void {
  emit('update:modelValue', name)
  emit('change', name)
  visible.value = false
}

function clear(): void {
  emit('update:modelValue', '')
  emit('change', '')
}

/** 每次打开都从空搜索开始：上次搜完没清就关掉，下次打开看到的是过滤后的子集，容易以为图标少了 */
function onShow(): void {
  keyword.value = ''
}

/*
 * 切换页签 / 路由时强制关闭面板。
 *
 * popover 的内容是 teleport 到 body 上的，而 KeepAlive 让页面失活时走的是
 * move(vnode, container, LEAVE)，moveTeleport 只在"重排"时才搬内容，失活只搬锚点
 * ——结果是页面被收进缓存了，面板还留在 body 上飘着。
 * 正常点页签时 popover 会因点击外部自己关掉，但刷新页签、程序化跳转这类不走点击
 * 的路径躲不过去，所以这里显式兜一次。
 */
const route = useRoute()
watch(() => route.fullPath, () => {
  visible.value = false
})
</script>

<style scoped lang="scss">
.icon-select {
  width: 100%;
}

.icon-select__field {
  display: flex;
  align-items: center;
  gap: 6px;
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  padding: 0 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background-color: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  transition: border-color var(--el-transition-duration-fast);

  &:hover {
    border-color: var(--el-border-color-hover);
  }

  &.is-placeholder .icon-select__label {
    color: var(--el-text-color-placeholder);
  }

  &.is-disabled {
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
    background-color: var(--el-fill-color-light);
    border-color: var(--el-border-color-lighter);
  }
}

.icon-select__icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
}

.icon-select__label {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.icon-select__arrow,
.icon-select__clear {
  flex-shrink: 0;
  color: var(--el-text-color-placeholder);
  transition: color var(--el-transition-duration-fast);
}

.icon-select__clear:hover {
  color: var(--el-color-danger);
}

.icon-select__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-select__grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 260px;
  padding: 2px;
  overflow-y: auto;
}

.icon-select__item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: var(--el-border-radius-base);
  transition: all var(--el-transition-duration-fast);

  &:hover {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }

  &.is-active {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-8);
    border-color: var(--el-color-primary);
  }
}

.icon-select__empty {
  padding: 24px 0;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  text-align: center;
}

.icon-select__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<!--
  popover 的内容会被 teleport 到 body，`.el-popover` 这个容器是 Element Plus 自己的
  节点、不带本组件的 data-v，scoped 样式够不着它，只能用 popper-class 写一段全局的。
  类名带 icon-select 前缀，不会误伤别处。
-->
<style lang="scss">
.icon-select-popper {
  padding: 10px 12px !important;
}
</style>
