/*
 * 弹框跟随页签：离开某个页签时把挂在 body 上的遮罩层藏起来，切回来再放出来。
 *
 * 为什么需要这一层：
 * Vue 的 KeepAlive 失活时调用 move(vnode, storageContainer, LEAVE)，但 moveTeleport
 * 只在 moveType === 2（重排）时才搬运内容，LEAVE 分支仅搬走锚点节点。
 * 所以 Teleport 到 body 的弹框（写了 append-to-body 的）会原地留在屏幕上，
 * 切到别的页签后仍然飘在上面 —— 那份 DOM 根本不在被缓存的那棵子树里。
 *
 * 页面里仍然优先「不要写 append-to-body」：
 * 就地渲染的弹框会跟着页面一起被搬进缓存容器，天然正确，还需要这里兜的只有漏网的。
 * 这里是兜底，不是主方案。
 */

/** 只处理直接挂在 body 下的遮罩层；就地渲染的弹框在页面 DOM 里，不归这里管 */
function bodyOverlays(): HTMLElement[] {
  return Array.from(document.body.children).filter(
    (el): el is HTMLElement => el instanceof HTMLElement && el.classList.contains('el-overlay')
  )
}

/**
 * 离开页签：把当前可见的遮罩层藏起来，并记下它属于谁。
 * 已经隐藏的跳过，避免把别的页签的归属覆盖掉。
 */
export function hideOverlaysOf(key: string): void {
  bodyOverlays().forEach((el) => {
    if (el.style.display === 'none') return
    el.dataset.tabKey = key
    el.style.display = 'none'
  })
}

/** 回到页签：只放出属于它的那些，元素已被移除的自然不在查询结果里 */
export function showOverlaysOf(key: string): void {
  bodyOverlays().forEach((el) => {
    if (el.dataset.tabKey !== key) return
    el.style.display = ''
    delete el.dataset.tabKey
  })
}
