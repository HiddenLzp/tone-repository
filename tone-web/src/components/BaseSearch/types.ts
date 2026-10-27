/**
 * 通用搜索组件的配置类型。
 *
 * <p>设计目标：把「表格上方那一排查询控件」从每个页面里手抄一遍，
 * 变成一份可声明的 fields 配置。页面只负责给出 query 对象和 loadData，
 * 控件渲染、回车查询、重置、日期区间拆分全部由组件内聚处理。
 */

/** 支持的控件类型 */
export type SearchFieldType = 'input' | 'select' | 'datetimerange' | 'tree-select'

export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

export interface TreeSelectProps {
  label?: string
  children?: string
}

/** 单个搜索项 */
export interface BaseSearchField {
  /** 绑定的字段名，直接读写 model[prop]；datetimerange 不需要（用 beginKey/endKey） */
  prop?: string
  /** 表单标签 */
  label?: string
  type: SearchFieldType
  placeholder?: string
  /** 控件宽度，数字按 px 处理；不传则用 defaultWidth 的档位 */
  width?: number | string
  /** type=select 必填 */
  options?: SelectOption[]
  clearable?: boolean
  disabled?: boolean

  /* ---- datetimerange 专用 ---- */
  /** 区间起始值写入 model 的哪个字段 */
  beginKey?: string
  /** 区间结束值写入 model 的哪个字段 */
  endKey?: string
  /** 默认与后端 LocalDateTime 反序列化格式对齐 */
  valueFormat?: string
  startPlaceholder?: string
  endPlaceholder?: string

  /* ---- tree-select 专用 ---- */
  treeData?: unknown[]
  treeProps?: TreeSelectProps
  nodeKey?: string
  /** 父子是否独立（部门单选场景通常要 true，否则只能选到叶子） */
  checkStrictly?: boolean
  multiple?: boolean

  /** 条件渲染，如仅当某个 dataScope 时才显示 */
  visible?: boolean
}

/** 状态下拉：正常/停用。列表页最常见的两个取值，避免 12 个页面各抄一遍 */
export const STATUS_NORMAL_OPTIONS: SelectOption[] = [
  { label: '正常', value: 0 },
  { label: '停用', value: 1 }
]

/** 结果下拉：成功/失败。日志类页面用 */
export const STATUS_RESULT_OPTIONS: SelectOption[] = [
  { label: '成功', value: 0 },
  { label: '失败', value: 1 }
]
