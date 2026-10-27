# tone-server（消息语气助手 · 后端）

JDK 17 + Spring Boot 4.1.0 + Spring Security 7 + MyBatis-Plus（boot4 starter）+ Spring AI 2.0.0 + MySQL 8 + Redis。

## 目录速览

```
com.lizp.tone
├─ common/          R<T> 统一响应、ErrorCode（31xxx 业务 / 32xxx 微信）、全局异常
├─ config/          SecurityConfig、BaseProperties(tone.*)、Jackson2Config（Boot4 兼容）、LlmConfig
├─ security/        管理台 JWT + RBAC（@RequiresPermissions 动态鉴权）
├─ module/agent/    语气 Agent 业务
│   ├─ config/      AgentProperties(tone.agent) / LlmProperties(tone.llm)
│   ├─ llm/         MockChatModel（tone.llm.mock=true 时装配）
│   ├─ prompt/      System Prompt（~600字）与各结构化调用模板
│   ├─ tool/        ToneAgentTools：detect_sensitive_words / analyze_tone / check_scene_rules / rewrite_text / save_version
│   ├─ service/     AgentOrchestrator（ReAct 循环 ≤8 轮）、AgentService（敏感词硬拦截→配额→循环→落库）
│   ├─ controller/  POST /api/agent/check、/api/agent/refine
│   └─ entity/mapper/  app_check_record / app_check_message
├─ module/wxapi/    小程序侧：wx 登录（code2session + dev mock）、JWT 拦截器、Redis 配额（免费3次+广告奖励）、/api/ad/config
└─ module/stats/    管理台看板：/agent/stats/overview|trend|top-users、/agent/records（分页/详情）
```

资源文件：`scene-rules.yml`（场景规则库）、`sensitive-words.txt`（本地敏感词库，可热改后重启生效）。

## 关键配置（application.yml `tone.*`）

| 配置 | 说明 |
|---|---|
| `tone.llm.mock` | true=Mock 模型（无需 Key）；false=真实调用，必须给 `TONE_LLM_API_KEY` |
| `tone.llm.base-url/model` | 智谱 `https://open.bigmodel.cn/api/paas/v4` + glm-4-flash；通义兼容模式见根 README |
| `tone.agent.max-rounds` | ReAct 循环上限（默认 8） |
| `tone.agent.daily-free-count / daily-reward-max` | 每日免费 3 次 / 奖励上限 10 |
| `tone.wx.appid/secret` | 为空时小程序登录走 dev 模式（稳定 mock openId） |
| `tone.wx.token-secret` | 小程序 JWT 独立密钥，生产必须覆盖 |
| `tone.ad.unit-id` | 激励视频广告位 ID，未配置时小程序隐藏广告入口 |

## Boot 4 迁移要点（相对 base-framework 模板）

1. `spring-boot-starter-aop` 被 Boot 4 移除 → 显式引 `aspectjweaver`；
2. Spring Security 7：`AuthorizationManager.check(...)` → `authorize(Supplier<? extends Authentication>, T)`；
3. Web 层默认 Jackson 3 → 补 `Jackson2Config`（jjwt/日志脱敏仍依赖 Jackson 2），且管理台 VO 不得用 Jackson 2 的 `JsonNode` 直接出参；
4. `@EnableCaching` 需要 `spring-boot-starter-cache` 才有默认 CacheManager；
5. Spring AI 的 OpenAI 全部自动装配类被排除，ChatModel 由 `LlmConfig` 按 mock 开关手动装配（Spring AI 2.0 基于 openai-java SDK 的 ClientOptions/OpenAIClient）。
