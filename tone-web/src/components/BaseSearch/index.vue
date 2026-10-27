<template>
  <!--
    外层容器负责"与页面背景分开"：内容区是灰底、页面卡片是白底，
    查询区再用一层极浅的填充色，三层由深到浅形成层次。
    plain 模式给已经身处 el-card 里的页面（如字典页）用，避免卡中卡。
  -->
  <div class="base-search" :class="{ 'base-search--plain': plain }">
    <!--
      回车查询挂在 el-form 上而不是每个控件上：keyup 会冒泡，
      无论焦点在 input 还是 date-picker，按回车都能触发一次查询。
    -->
    <el-form
      :model="model"
      inline
      class="base-toolbar"
      :label-width="labelWidth"
      @keyup.enter="handleSearch"
    >
      <slot name="prepend" />

      <el-form-item v-for="f in visibleFields" :key="fieldKey(f)" :label="f.label">
        <!-- ① 文本输入 -->
        <el-input
          v-if="f.type === 'input'"
          :model-value="model[f.prop!] as string"
          :placeholder="placeholderOf(f)"
          :clearable="f.clearable !== false"
          :disabled="f.disabled"
          :style="styleOf(f)"
          @update:model-value="(v: string | number) => set(f.prop!, v)"
        />

        <!-- ② 下拉选择 -->
        <el-select
          v-else-if="f.type === 'select'"
          :model-value="model[f.prop!]"
          placeholder="全部"
          :clearable="f.clearable !== false"
          :disabled="f.disabled"
          :style="styleOf(f)"
          @update:model-value="(v: unknown) => set(f.prop!, v)"
        >
          <el-option
            v-for="o in f.options ?? []"
            :key="o.value"
            :label="o.label"
            :value="o.value"
            :disabled="o.disabled"
          />
        </el-select>

        <!-- ③ 日期区间：值内聚在组件里，页面不再需要 dateRange ref -->
        <el-date-picker
          v-else-if="f.type === 'datetimerange'"
          :model-value="rangeValue(f)"
          type="datetimerange"
          :value-format="f.valueFormat ?? 'YYYY-MM-DDTHH:mm:ss'"
          :start-placeholder="f.startPlaceholder ?? '开始'"
          :end-placeholder="f.endPlaceholder ?? '结束'"
          :clearable="f.clearable !== false"
          :disabled="f.disabled"
          :style="styleOf(f)"
          @update:model-value="(v: [string, string] | null) => setRange(f, v)"
        />

        <!-- ④ 树形选择 -->
        <el-tree-select
          v-else-if="f.type === 'tree-select'"
          :model-value="model[f.prop!]"
          :data="(f.treeData ?? []) as any[]"
          :props="f.treeProps ?? { label: 'label', children: 'children' }"
          :node-key="f.nodeKey ?? 'id'"
          :check-strictly="f.checkStrictly"
          :multiple="f.multiple ?? false"
          :render-after-expand="false"
          :disabled="f.disabled"
          check-on-click-node
          clearable
          placeholder="全部"
          :style="styleOf(f)"
          @update:model-value="(v: unknown) => set(f.prop!, v)"
        />
      </el-form-item>

      <el-form-item>
        <el-button
          v-if="showSearch"
          type="primary"
          :icon="Search"
          :loading="loading"
          @click="handleSearch"
        >
          {{ searchText }}
        </el-button>
        <el-button v-if="showReset" :icon="Refresh" @click="reset">
          {{ resetText }}
        </el-button>
        <slot name="extra" />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import type { BaseSearchField, SearchFieldType } from './types'

interface Props {
  /** 页面里的 reactive query 对象，组件直接读写它，不维护副本 */
  model: Record<string, unknown>
  fields: BaseSearchField[]
  /** 是否带分页：true 时点查询会把 model.pageNum 置 1。树表类页面传 false */
  paged?: boolean
  labelWidth?: string
  searchText?: string
  resetText?: string
  showSearch?: boolean
  showReset?: boolean
  loading?: boolean
  /** 已经在卡片/面板里时传 true：只保留布局，不要容器底色与边框 */
  plain?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  paged: true,
  labelWidth: 'auto',
  searchText: '查询',
  resetText: '重置',
  showSearch: true,
  showReset: true,
  loading: false,
  plain: false
})

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()

/** 各类型控件的默认宽度档位，替掉之前散落在 12 个页面里的 100~340 硬编码 */
const defaultWidth: Record<SearchFieldType, number> = {
  input: 160,
  select: 120,
  datetimerange: 340,
  'tree-select': 180
}

const visibleFields = computed<BaseSearchField[]>(() =>
  props.fields.filter((f) => f.visible !== false)
)

function fieldKey(f: BaseSearchField): string {
  return `${f.type}:${f.prop ?? ''}:${f.beginKey ?? ''}`
}

function styleOf(f: BaseSearchField): Record<string, string> {
  const w = f.width ?? defaultWidth[f.type]
  return { width: typeof w === 'number' ? `${w}px` : w }
}

function placeholderOf(f: BaseSearchField): string {
  return f.placeholder ?? f.label ?? ''
}

function set(prop: string, value: unknown): void {
  props.model[prop] = value
}

/* ---------------- 日期区间 ---------------- */

/** 组件内部持有区间值，key 为 `${beginKey}|${endKey}` */
const ranges = reactive<Map<string, [string, string] | null>>(new Map())

const rangeKeyOf = (f: BaseSearchField): string => `${f.beginKey}|${f.endKey}`

function rangeValue(f: BaseSearchField): [string, string] | null {
  return ranges.get(rangeKeyOf(f)) ?? null
}

function setRange(f: BaseSearchField, v: [string, string] | null): void {
  ranges.set(rangeKeyOf(f), v ?? null)
  writeRange(f, v ?? null)
}

/** 把区间拆成 beginKey/endKey 写回页面 query */
function writeRange(f: BaseSearchField, v: [string, string] | null): void {
  if (f.beginKey) props.model[f.beginKey] = v?.[0]
  if (f.endKey) props.model[f.endKey] = v?.[1]
}

/** 查询前调用，保证日期面板开着时的值也能同步出去 */
function flushRange(): void {
  props.fields
    .filter((f) => f.type === 'datetimerange')
    .forEach((f) => writeRange(f, ranges.get(rangeKeyOf(f)) ?? null))
}

/* ---------------- 重置：只重置 fields 声明过的 key ---------------- */

/**
 * 逐个 key 取快照，而不是 `{ ...props.model }`：
 * ① 展开运算符会丢掉值为 undefined 的 key（如 `status: undefined`），
 *    重置后就变成"字段消失"而不是"字段为 undefined"，发给后端是两种情况；
 * ② 像字典页 dataQuery.dictType 这种"非搜索字段"会被误清，导致右侧列表查空。
 */
const snapshot: Record<string, unknown> = {}
for (const f of props.fields) {
  if (f.prop) snapshot[f.prop] = props.model[f.prop]
  if (f.beginKey) snapshot[f.beginKey] = props.model[f.beginKey]
  if (f.endKey) snapshot[f.endKey] = props.model[f.endKey]
}

function reset(): void {
  Object.keys(snapshot).forEach((k) => {
    props.model[k] = snapshot[k]
  })
  ranges.clear()
  emit('reset')
}

/* ---------------- 查询 ---------------- */

function handleSearch(): void {
  flushRange()
  if (props.paged && 'pageNum' in props.model) {
    props.model.pageNum = 1
  }
  emit('search')
}

defineExpose({ reset, flushRange })
</script>

<style scoped lang="scss">
/*
 * 查询区容器：内容区是灰底(--el-bg-color-page)，页面卡是白底(--el-bg-color)，
 * 这里再垫一层更浅的填充色，让「背景 → 卡片 → 查询区」形成三层层次。
 * 用 el-fill-color-lighter 而不是写死色值，暗色模式下自动跟着变。
 */
.base-search {
  padding: 12px 12px 0;
  margin-bottom: 12px;
  background-color: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;

  /* 内部 el-form 自带 margin-bottom，会和容器的 padding 叠成双倍间距 */
  :deep(.el-form.base-toolbar) {
    margin-bottom: 0;
  }

  /* 已在卡片内时使用：只留布局，避免卡中卡 */
  &--plain {
    padding: 0;
    margin-bottom: 12px;
    background-color: transparent;
    border: none;
  }
}
</style>
