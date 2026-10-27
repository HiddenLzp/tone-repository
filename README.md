# 措辞师 · 消息语气检查与改写 Agent

输入一段话 + 选择沟通场景，Agent 自动诊断语气问题（礼貌度/专业度/情感倾向）、按场景规则校验、生成 3 个风格的改写版本，并支持"再委婉一点"式的多轮微调。商业化机制：每日免费 3 次，超出后看激励视频广告补 1 次；Web 管理台监控用户量、Token 成本与广告转化。

## 工程结构

```
├─ tone-server/    后端：JDK 17 + Spring Boot 4.1.0 + Spring AI 2.0.0 + MyBatis-Plus + MySQL + Redis
├─ tone-web/       管理台前端：Vue 3 + Vite 6 + Element Plus + ECharts（动态菜单，无需改路由表）
├─ tone-uniapp/    小程序：uni-app (Vue3 + Vite)，编译目标 mp-weixin
├─ docs/sql/       数据库脚本（含业务表与菜单）
└─ docs/template-docs/  底座脚手架（base-framework）的原始文档，供参考
```

## 快速开始

### 1. 数据库（MySQL 8）

```bash
mysql -uroot -p
# 依次执行（顺序不能换）：
#   docs/sql/schema.sql        框架表（sys_*）
#   docs/sql/data.sql          框架数据（菜单/角色/字典/任务），admin 密码是占位符
#   docs/sql/agent_schema.sql  业务表（app_wx_user / app_check_record / app_check_message / app_ad_reward）
#   docs/sql/agent_menu.sql    业务菜单（语气助手 → 数据看板 / 检查记录）
```

`data.sql` 里 admin 的密码是 `__PENDING_BCRYPT__` 占位符，用任意 BCrypt 生成器生成 `admin123` 的哈希后 UPDATE 即可：

```sql
UPDATE sys_user SET password='$2a$10$<你的哈希>' WHERE username='admin';
```

### 2. 后端（端口 8080）

```bash
cd tone-server
# dev 配置默认：MySQL 127.0.0.1:3306/tone_agent（root/123456），Redis 192.168.0.200:6379
# 按需改 application-dev.yml
mvn spring-boot:run
# 默认 tone.llm.mock=true，无需 API Key 即可跑通全流程
```

### 3. 管理台（端口 5173）

```bash
cd tone-web
npm install
npm run dev        # admin / admin123 登录，侧边栏出现「语气助手」
```

### 4. 小程序

```bash
cd tone-uniapp
npm install
npm run dev:mp-weixin
# 微信开发者工具 → 导入项目 → 目录选 dist/dev/mp-weixin
# AppID 用「测试号」；开发者工具详情里勾选「不校验合法域名」（dev 模式后端在 localhost:8080）
```

小程序端无需真实 appid 也能联调：后端未配置 `tone.wx.appid` 时走 dev 登录（按设备号生成稳定 mock openId）。

## 接入真实 LLM（智谱 / 通义）

1. `application.yml` 里改 `tone.llm.mock: false`；
2. `provider/base-url/model` 三选一预设或自定义：

| provider | base-url | model 示例 |
|---|---|---|
| zhipu | https://open.bigmodel.cn/api/paas/v4 | glm-4-flash / glm-4-plus |
| dashscope | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus / qwen-turbo |

3. 启动时注入 Key：环境变量 `TONE_LLM_API_KEY`（或直接写 `tone.llm.api-key`）。

> 真实链路已实测打通（假 Key 探测到智谱端点返回 401「令牌已过期或验证不正确」）。注意 openai-java 的两个坑，已在 `LlmConfig` 里处理：`ClientOptions.httpClient` 必填（用 spring-ai-openai 的 `SpringAiOpenAiHttpClient`）；`OpenAiChatModel.builder()` 必须同时给 sync + async 两个客户端，只给 sync 时框架会用空凭证隐式构建 async 导致 "At least one credential source must be specified"。模型侧 401/429/5xx 会映射为 40001「模型服务调用失败：...」并退还配额。

## 接口一览（小程序端，JWT 鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/wx/login | wx.login 的 code 换 token（模拟登录由 `tone.wx.mock-login` 显式控制，dev 默认 true，与 appid 是否配置解耦） |
| GET | /api/quota/check | 今日配额：freeUsed/freeTotal/rewardClaimed/rewardUsed |
| POST | /api/quota/reward | 看完激励视频后 +1 次 |
| POST | /api/quota/reset | **[开发]** 重置当前用户今日配额（仅 dev-tools-enabled=true 时装配） |
| GET | /api/ad/config | 广告位配置（unitId 只在后端配置） |
| POST | /api/agent/check | 语气检查（同步）：诊断 + 场景校验 + 3 版本 |
| POST | /api/agent/refine | 微调（同步）：对当前版本说"再委婉一点" |
| POST | /api/agent/check/stream | 语气检查 **SSE 流式**：stage 阶段事件（工具实际调用时发出）→ result → complete |
| POST | /api/agent/refine/stream | 微调 **SSE 流式**：delta 逐字输出 → result → complete |

> 流式接口的传输形式是「SSE over POST + chunked」：小程序端用 `wx.request({ enableChunked: true })` 消费（见 tone-uniapp `utils/stream.js`，含跨 chunk 的 UTF-8 增量解码与 401 重登重试）。后端在专用线程池执行 Agent（一次真实检查 60 秒+，不能占用 Tomcat 工作线程），**离开页面时客户端 abort，服务端连接随之关闭**。微调的 prompt 约定模型直接输出正文（不带 JSON 外壳），delta 才能逐字渲染；检查的最终产物是结构化 JSON，因此"流式"是阶段流而非 token 流。

管理台接口（走模板 RBAC）：`/agent/stats/overview|trend|scene-stats|quota-funnel|top-users`、`/agent/records`（分页/详情）、`/agent/sensitive/reload`（敏感词热加载）。

## 商业化机制

- 每日免费 3 次（`tone.agent.daily-free-count`，Redis key 按 openId+日期，TTL 48h）
- 看激励视频 +1（上限 `tone.agent.daily-reward-max`，写入 app_ad_reward 流水供看板统计）
- 所有配额事实（免费消耗/奖励消耗/用尽拒绝/广告领取/失败退还）落 `app_quota_event` 流水，看板"配额漏斗"单一来源走 MySQL，Redis 计数只是实时判定缓存
- 广告位 ID：`TONE_AD_UNIT_ID` 环境变量或 `tone.ad.unit-id`；未配置时小程序隐藏广告入口、reward 接口按 dev 直接发放

## 生产部署（Nginx 样例）

```nginx
server {
  listen 443 ssl;
  server_name your.domain.com;

  # 管理台静态资源
  location / {
    root /opt/tone-web/dist;
    try_files $uri $uri/ /index.html;
  }

  # 管理台接口：剥掉 /api 前缀转发（与前端 Vite 代理行为一致）
  location /api/ {
    proxy_pass http://127.0.0.1:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }
}
```

小程序端生产地址改 `tone-uniapp/src/config/env.js` 的 `BASE_URL` 为 `https://your.domain.com/api`（注意小程序端接口带 `/api` 前缀，上面 `/api/` 的转发规则正好兼容）；并在微信公众平台配置 request 合法域名。生产必须覆盖的环境变量：`TONE_SECURITY_JWT_SECRET`、`TONE_WX_TOKEN_SECRET`、`TONE_LLM_API_KEY`（可选 `TONE_WX_APPID/TONE_WX_SECRET/TONE_AD_UNIT_ID`）。

## 已验证状态

- 后端 Boot 4.1.0 迁移完成，mvn 编译通过；curl 全链路冒烟通过（登录 → 配额 → mock 检查 → 微调 → 广告奖励 → 第 5 次被拒 → 敏感词拦截 → 看板统计）
- 管理台浏览器实测通过：admin 登录、看板卡片/趋势/排行图表渲染、检查记录分页与详情
- 小程序 `build:mp-weixin` 编译通过，微信开发者工具导入 dist 即可预览
- 体验修复：检查进度反馈（阶段提示）、看完广告自动开检（须完整观看）、微调最新版复制/采用回退（/api/agent/restore）、微调历史本地持久化、评分参照标签、失败请求落库（看板可见失败率）、敏感词命中词落库、看板昨日对比/平均轮次/场景分布/更新时间、记录页时间筛选与结构化对话明细、生产环境默认关闭 springdoc、敏感词库热加载接口 POST /agent/sensitive/reload

## 上线清单（个人主体小程序）

上线前按顺序过一遍（前两项是审核硬门槛）：

### 1. 内容安全检测（已实现，上线即生效）

- 提交原文 + AI 改写结果都会过 `security.msgSecCheck` 2.0（个人主体可用），
  代码在 `WxSecService`，不通过与本地敏感词同样走 blocked 路径且不消耗配额；
- 前提：配置真实 appid/secret（`TONE_WX_APPID` / `TONE_WX_SECRET`）并把 `tone.wx.mock-login` 改为 false；
- mock 登录模式下自动跳过（假 openid 无法调用 2.0 接口）；
- 策略：微信接口故障时放行（fail-open）并打 WARN——安全接口故障不能打死业务，可改 `WxSecService.FAIL_OPEN`。

### 2. 用户隐私保护指引（微信公众平台后台配置）

1. 登录 mp.weixin.qq.com → 设置 → 服务内容声明 → 用户隐私保护指引；
2. 按实际收集项勾选并填写用途：
   - **头像昵称**（chooseAvatar/nickname 填写能力，用户主动提供，用于展示）
   - **设备信息**（User-Agent，用于安全与统计）
   - **IP 地址**（用于安全与归属地统计）
   - 注意声明「用于内容安全检测」（openid 传给 msgSecCheck）
3. 提交后，基础库 2.32.3+ 在触达隐私接口时会**自动弹出官方隐私授权弹窗**，
   无需开发者写 UI；如需自定义监听 `wx.onNeedPrivacyAuthorization`。

### 3. AI 生成内容声明（已实现）

结果页底部已有「内容由 AI 生成，仅供参考」——小程序 AI 类目审核要求。

### 4. 分享（已实现）

首页/结果页均已开启右上角转发与朋友圈分享；结果页有页内分享按钮。
分享卡片只推工具本身、**不带用户原文**（隐私优先）。

### 5. 广告位申请

分享带来独立访客达标后，在流量主后台申请激励视频广告位，
把 ID 配到 `TONE_AD_UNIT_ID`（或环境变量），小程序端自动显示"看视频+1"入口。

## 运营工具与稳定性（2026-10-26 第二批）

### LLM 自动降级（FallbackChatModel）

- 配置（application.yml `tone.llm.fallback.*`，留空不启用）：
  ```yaml
  tone.llm.fallback:
    model: qwen-flash                                     # 备用模型
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1  # 可选，默认复用主端点
  ```
- 主模型失败（超时/限流/5xx/模型名错误）自动换备用**重试一次**，用户无感成功（实测：主模型配错 → `404: Model not exist.` → qwen-flash 兜底 200）；备用模型名还可在「运营设置」在线改。
- 实现坑位（注释里都写了）：ChatClient/Prompt.Builder 会把 options 收编成 `DefaultChatOptions`，OpenAiChatModel 强转会 CCE——运行时模型注入必须放在 ChatModel 包装层（`RuntimeOptionsChatModel`），不要用 `.options(builder)`。

### 运营设置（管理台「语气助手 → 运营设置」，保存即生效无需重启）

- 模型 / 温度 / 备用模型 / 每日免费次数 / 每日奖励上限 / Token 日告警阈值（sys_config 存储，30 分钟缓存 + 写清除）
- 敏感词库在线编辑（每行一个词）：保存写 `config/sensitive-words.txt`（外部文件优先于 jar 内默认），检查链路立即生效（实测保存→检查 blocked ✓）
- Token 告警：当日消耗 ≥ 阈值时看板顶部红色横幅；另有定时任务 `tokenAlertTask.checkDaily()`（每日 21:30，管理台「任务调度」可改）打告警日志

### 小程序

- 最近记录行新增「重跑」：回填原文+场景立即检查（显式动作消耗一次配额；次数不足走配额弹窗）
