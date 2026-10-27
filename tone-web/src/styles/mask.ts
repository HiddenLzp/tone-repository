/**
 * 遮罩范围：loading 与弹窗（对话框/抽屉/确认框）的遮罩盖到哪一层。
 *
 * - tab（默认）：遮罩对齐内容区那一块矩形，也就是"当前页签的页面"。
 *   开着多页签时，顶栏、侧边栏、页签栏依然可以点，符合"页面上有一层模态"
 *   而不是"整个系统卡住"的直觉。
 * - page：盖住整个视口，等同于 Element Plus 的默认行为。
 *
 * 实现方式说明（很重要，别改成操作 el-dialog 的 append-to-body）：
 * 弹窗的根节点 Eleport 到 body 上，`.el-overlay` 是 position:fixed。
 * fixed 元素只能以其祖先里的"包含块"定位，而 teleport 已经把它挪出去了，
 * 所以想把它限制在某个容器里，唯一干净的办法是改它 fixed 矩形的四条边
 * ——这几条边用 CSS 变量喂进去，见 styles/index.scss。
 * 就地渲染（append-to-body=false）做不到同一件事：容器开了 overflow 滚动，
 * 抽屉和长对话框会被裁掉。
 */

export type MaskScope = 'page' | 'tab'

/** 默认作用域走环境变量：整站统一口味，用户可在主题面板里单独覆盖 */
export const DEFAULT_MASK_SCOPE: MaskScope =
  import.meta.env.VITE_MASK_SCOPE === 'page' ? 'page' : 'tab'

/** 写在 html 上，样式按这个属性决定用哪套定位 */
export function applyMaskScope(scope: MaskScope): void {
  document.documentElement.dataset.maskScope = scope
}

/**
 * tab 模式下遮罩要贴齐的那个矩形：内容区在视口里的位置。
 * 由 layout 在挂载后与每次布局变化时调用。
 */
export function applyMaskInset(top: number, right: number, bottom: number, left: number): void {
  const style = document.documentElement.style
  style.setProperty('--base-mask-top', `${Math.round(top)}px`)
  style.setProperty('--base-mask-right', `${Math.round(right)}px`)
  style.setProperty('--base-mask-bottom', `${Math.round(bottom)}px`)
  style.setProperty('--base-mask-left', `${Math.round(left)}px`)
}
