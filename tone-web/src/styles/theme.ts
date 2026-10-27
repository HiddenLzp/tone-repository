/**
 * 主题：运行时切换 Element Plus 主色 + 暗色模式。
 *
 * 为什么是「运行时写 CSS 变量」而不是「重新编译 scss」：
 * 1. 需求里用户要能自定义颜色，编译期方案根本做不到；
 * 2. 本项目引的是预编译好的 element-plus/dist/index.css，要编译就得整套换成
 *    unplugin-vue-components + ElementPlusResolver，改造面和收益完全不成比例；
 * 3. 项目自身的配色一律写 var(--el-*)，改根变量就能整体跟随，不需要额外同步。
 */

/** 预设色：后台管理场景里最常见的几种主色 */
export interface ThemePreset {
  name: string
  color: string
}

export const THEME_PRESETS: ThemePreset[] = [
  { name: '默认蓝', color: '#409eff' },
  { name: '青翠绿', color: '#00b96b' },
  { name: '活力橙', color: '#ff7a45' },
  { name: '典雅紫', color: '#722ed1' },
  { name: '清新青', color: '#13c2c2' }
]

export const DEFAULT_THEME_COLOR = THEME_PRESETS[0].color

/** 主色上用什么颜色的文字才看得清：亮色配深字，暗色配白字 */
const CONTRAST_VAR = '--base-primary-contrast'

// ==================== 颜色计算 ====================

interface RGB {
  r: number
  g: number
  b: number
}

/** 支持 #rgb 与 #rrggbb 两种写法，解析不了就返回 null 交给调用方兜底 */
function parseHex(hex: string): RGB | null {
  const s = hex.trim().replace('#', '')
  if (s.length === 3) {
    const [r, g, b] = s.split('')
    return {
      r: parseInt(r + r, 16),
      g: parseInt(g + g, 16),
      b: parseInt(b + b, 16)
    }
  }
  if (s.length === 6) {
    return {
      r: parseInt(s.slice(0, 2), 16),
      g: parseInt(s.slice(2, 4), 16),
      b: parseInt(s.slice(4, 6), 16)
    }
  }
  return null
}

function toHex({ r, g, b }: RGB): string {
  return `#${[r, g, b].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`
}

/**
 * 按权重混色。weight 是 target 的占比，0 = 全是 base，1 = 全是 target。
 * 与 Element Plus 主题里 mix() 的算法一致，算出来的衍生色和官方对得上。
 */
function mix(base: RGB, target: RGB, weight: number): RGB {
  return {
    r: base.r * (1 - weight) + target.r * weight,
    g: base.g * (1 - weight) + target.g * weight,
    b: base.b * (1 - weight) + target.b * weight
  }
}

const WHITE: RGB = { r: 255, g: 255, b: 255 }
const BLACK: RGB = { r: 0, g: 0, b: 0 }
/** 暗色主题的混色基准色，取自 Element Plus 暗色主题 light-N 的算法 */
const DARK_BASE: RGB = { r: 0x14, g: 0x14, b: 0x14 }

/**
 * 感知亮度（相对亮度简化版，只用来判断深浅，够用且不引第三方库）。
 * 系数取自 WCAG 的相对亮度公式。
 */
function luminance({ r, g, b }: RGB): number {
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255
}

/** light-3/5/7/8/9、dark-2 这几个档位与 Element Plus 变量名一一对应 */
const LIGHT_LEVELS = [3, 5, 7, 8, 9] as const

/**
 * 应用主色。
 *
 * 关键点一：Element Plus 的 light-3 ~ light-9、dark-2 在预编译 css 里是**硬编码死值**，
 * 不会跟着 --el-color-primary 变。只改主色一个变量的话，hover 态、focus 环、
 * 下拉浅底色会残留原来的蓝色，所以这里必须一次写全套。
 *
 * 关键点二：dark 参数不能省。写到这里的是 html 的**内联样式**，优先级压过
 * `html.dark{...}` 里暗色主题的取值；而两种主题下衍生色的混色方向是相反的——
 * 亮色里 light-9 是"主色混 90% 白"，暗色里却是"主色混 90% #141414"。
 * 不看暗色标记直接混白的话，暗色模式下 --el-menu-hover-bg-color 会变成接近纯白的
 * 浅蓝，鼠标划过侧边栏就像打了一盏白灯，非常刺眼。
 *
 * 关键点三：因此**切换暗色后必须重新调用本函数**（见 app store 的 setDark），
 * 否则先开暗色再改主色、或反过来，两者会互相覆盖。
 */
export function applyPrimaryColor(color: string, dark = false): void {
  const rgb = parseHex(color)
  if (!rgb) return

  const root = document.documentElement
  const hex = toHex(rgb)
  root.style.setProperty('--el-color-primary', hex)
  root.style.setProperty('--el-color-primary-rgb', `${rgb.r}, ${rgb.g}, ${rgb.b}`)

  // 亮色：light-N 往白混、dark-2 往黑混；暗色：正好反过来
  const lightTarget = dark ? DARK_BASE : WHITE
  const darkTarget = dark ? WHITE : BLACK
  LIGHT_LEVELS.forEach((n) => {
    root.style.setProperty(`--el-color-primary-light-${n}`, toHex(mix(rgb, lightTarget, n / 10)))
  })
  root.style.setProperty('--el-color-primary-dark-2', toHex(mix(rgb, darkTarget, 0.2)))

  // 主色偏亮时（如自定义成浅黄），配白字会看不清，这里给出配套的前景色
  root.style.setProperty(CONTRAST_VAR, luminance(rgb) > 0.62 ? '#1f2329' : '#ffffff')
}

/** 暗色模式开关：Element Plus 的暗色变量挂在 html.dark 上 */
export function applyDarkMode(dark: boolean): void {
  document.documentElement.classList.toggle('dark', dark)
}
