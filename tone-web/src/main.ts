import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
// 暗色变量：只在 html.dark 生效，与亮色共存不冲突
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'nprogress/nprogress.css'

import App from './App.vue'
import router from './router'
import { setupDirectives } from './directives'
import './styles/index.scss'
import { DEFAULT_THEME_COLOR, applyDarkMode, applyPrimaryColor } from './styles/theme'
import { DEFAULT_MASK_SCOPE, applyMaskScope } from './styles/mask'
import { readPersistedApp } from './stores/app'

/*
 * 挂载前先把主题打上去，否则首屏会先闪一下默认蓝。
 * 这里读 localStorage 而不是 store：pinia 还没装，顺序上拿不到 store 实例。
 * readPersistedApp 读的正是 app store 的持久化快照（同一个 key），
 * 所以不需要额外维护一份"主题专用"的存储，两边不会对不上。
 * 顺序有讲究：主色的衍生色要按暗/亮分别计算，所以先把 dark 读出来传进去。
 */
const stored = readPersistedApp()
applyPrimaryColor(stored.themeColor || DEFAULT_THEME_COLOR, stored.dark)
applyDarkMode(stored.dark)
applyMaskScope(stored.maskScope || DEFAULT_MASK_SCOPE)

const app = createApp(App)

// 全量注册图标：后台管理页面图标用量大且分散，按需引入要维护一份很长的白名单，不划算
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

/*
 * 持久化插件：store 里写 `persist: {...}` 即可把状态落到 localStorage。
 * 哪些 store 该持久化由各 store 自己声明（见 stores/ 下各文件），
 * 这里不做全局开启——token、路由、页签这类"必须在正确时机重新校验"的状态
 * 被自动恢复反而会出问题。
 */
app.use(createPinia().use(piniaPluginPersistedstate))
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
setupDirectives(app)

app.mount('#app')
