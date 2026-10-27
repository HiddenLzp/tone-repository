# tone-web（消息语气助手 · 管理台前端）

Vue 3.5 + Vite 6 + TypeScript + Pinia + Element Plus 2.8 + ECharts 5。底座能力（登录/动态路由/多页签/权限指令）见 `../docs/template-docs/WEB.md`。

## 新增业务页面

- `src/api/agent.ts`：看板与检查记录接口封装
- `src/views/agent/dashboard/index.vue`：今日概览 4 卡 + 近 7 天请求量/Token 折线图 + Top20 柱状图 + 30s 自动刷新开关
- `src/views/agent/records/index.vue`：检查记录分页（场景/openId/状态筛选）+ 详情抽屉（评分、问题、版本、对话明细）

菜单与按钮权限来自后端 `sys_menu`（`docs/sql/agent_menu.sql`）：`agent:stats:view`、`agent:records:list/query`。新增页面 = 建文件 + 配菜单，无需改路由表。

## 命令

```bash
npm install
npm run dev       # http://localhost:5173，代理 /api → localhost:8080（剥前缀）
npm run build     # vue-tsc 类型检查 + 打包 dist/
npm run typecheck
```
