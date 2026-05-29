# 飞书超级助手机器人 — 开发日志

版本 1.0 | 2026-05-15 → 2026-05-24

┌──────────┬──────────────────────────────────────────┬─────────────────────────────────┐
│   模块   │                   功能                   │            核心技术             │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 基础指令 │ /help /weather /translate /group /search │ RestTemplate + JSON 解析        │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 日程     │ /schedule                                │ MySQL + OAuth 2.0 + 飞书日历    │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ AI 搜索  │ /search-ai                               │ RAG + DeepSeek                  │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 权限控制 │ RBAC + @RequireRole                      │ AOP 切面 + 4 角色层级           │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 多轮对话 │ 上下文记忆                               │ Redis + AI 意图解析             │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 消息广播 │ /broadcast                               │ 一对多推送 + 群名映射           │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 审批提醒 │ 定时催办                                 │ @Scheduled + 私聊               │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 代码审查 │ /review + Gitee Webhook                  │ AI 审查 + 自动触发              │
├──────────┼──────────────────────────────────────────┼─────────────────────────────────┤
│ 插件框架 │ 指令注册                                 │ CommandPlugin + Spring 自动发现 │
└──────────┴──────────────────────────────────────────┴─────────────────────────────────┘

52 个 Java 文件，7 个 Maven 模块，14 张表。从设计到交付，独立完成的企业级飞书机器人。

## 项目状态总览（2026-05-24）

### 指令清单

| 指令 | 状态 | 关键技能 |
|------|------|---------|
| `/help` | ✅ | Plugin 自动发现 |
| `/weather` | ✅ | GET 调 Open-Meteo |
| `/group` | ✅ | POST 调飞书建群 API |
| `/schedule` | ✅ | MySQL 写入 + OAuth 2.0 + 飞书日历同步 |
| `/search` | ✅ | MyBatis-Plus LIKE 模糊搜索 |
| `/translate` | ✅ | 百度翻译 API（MD5 签名） |
| `/search-ai` | ⚠️ | 代码 OK，DNS 待修 |
| `/broadcast` | ✅ | @RequireRole + 一对多推送 + 群名映射 |
| `/review` | ✅ | AI 代码审查 — NPE/资源/异常/线程安全 |

### 系统功能

| 组件 | 状态 | 说明 |
|------|------|------|
| RBAC 权限控制 | ✅ | @RequireRole + AOP + SUPER_ADMIN 层级 |
| 多轮对话上下文 | ✅ | Redis + AI 意图解析 + 参数补全 |
| 审批催办 | ✅ | @Scheduled + Webhook + 私聊提醒 |
| 可扩展指令框架 | ✅ | CommandPlugin 接口 + Spring 自动发现 |

---

## 2026-05-20 — 功能 11：Gitee Webhook 自动代码审查（第二层）

### 新增文件（3 个）

| 模块 | 文件 | 说明 |
|------|------|------|
| api/controller | `GiteeWebhookController.java` | Gitee Webhook 端点 /webhook/gitee |
| integration/client | `GiteeClient.java` | 调 Gitee API 获取 commit diff |
| core/service | `GitReviewService.java` | 提取 diff → AI 审查 → 发飞书群 |

### 设计决策

- Gitee push 事件不含代码 diff → 调 Gitee compare API 获取实际变更
- Gitee token 可选：不配则只用文件列表 + commit message 做轻量审查
- 审查结果发到配置的飞书群（`feishu.review-chat-id`）

---

## 2026-05-20 — 功能 10：/review AI 代码审查

### 新增文件

| 模块 | 文件 | 说明 |
|------|------|------|
| core/handler | `ReviewHandler.java` | /review 指令处理器 |
| core/service | `GitReviewService.java` | 提取 diff → AI 审查 → 发飞书群 |

### 设计决策

- 支持手动触发（`/review owner/repo`）和 Gitee Webhook 自动触发
- AI 审查聚焦 4 类问题：NPE 风险、资源泄漏、异常处理、线程安全
- Prompt 定死了输出格式（评分/风险清单/建议），减少 AI 幻觉

---



## 2026-05-19 — 功能 9：多轮对话上下文（Redis + AI）

### 新增文件（2 个）

| 模块 | 文件 | 说明 |
|------|------|------|
| common/dto | `DialogContext.java` | 上下文数据结构，含 Turn 内部类 |
| core/service | `DialogService.java` | **核心：Redis存取 + AI意图解析 + 参数补全 + 路由执行** |

### 修改文件（2 个）

| 文件 | 改了什么 |
|------|---------|
| `common/.../CommandContext.java` | 新增 `contextArgs` 字段（AI 结构化参数透传） |
| `api/.../WebhookController.java` | 注入 DialogService，斜杠指令后存上下文，非指令走延续模式 |

### 设计决策

- **上下文存 Redis 而非内存**：服务重启不丢，多实例共享（目前单体但预留扩展）
- **AI 做意图解析**：非 / 开头消息用大模型理解意图，比正则灵活（"那明天呢"→查天气、"翻成日文"→翻译）
- **参数补全策略**：AI 优先（AI 返回的参数直接信），Redis 兜底（AI 没返回的用上轮上下文补）
- **Handler 零改动**：上下文延续统一由 DialogService 处理，构造标准 CommandContext 走 CommandRouter
- **TTL 10 分钟**：一个话题的正常持续时长，过期自动清
- **5 轮封顶**：防止给 AI 的历史太长导致 prompt 超限

---

## 2026-05-19 — 功能 8：权限控制（RBAC + AOP）

### 新增文件（9 个）

| 模块 | 文件 | 说明 |
|------|------|------|
| infrastructure/entity | `BotUser.java` | bot_user 表 Entity |
| infrastructure/entity | `BotRole.java` | bot_role 表 Entity |
| infrastructure/entity | `BotUserRole.java` | bot_user_role 关联表 Entity |
| infrastructure/mapper | `BotUserMapper.java` | 含 `selectByOpenId()` |
| infrastructure/mapper | `BotRoleMapper.java` | 含 `selectByCode()` |
| infrastructure/mapper | `BotUserRoleMapper.java` | 含 `hasRole()`（JOIN 查询） |
| auth/annotation | `RequireRole.java` | 权限注解 `@RequireRole("ADMIN")` |
| auth/aspect | `RoleCheckAspect.java` | **AOP 切面，自动拦截 execute() 校验角色** |
| auth/interceptor | `UserAutoRegister.java` | **新用户首次发消息自动注册** |

### 修改文件（2 个）

| 文件 | 改了什么 |
|------|---------|
| `feishu-bot-auth/pom.xml` | 新增 infrastructure 和 spring-boot-starter-aop 依赖 |
| `api/.../WebhookController.java` | 注入 UserAutoRegister，processCommand() 加 ensureUser() |

### 设计决策

- **RBAC（角色-权限模型）**：用户挂角色，角色决定能执行哪些指令。不是直接给每个用户配权限（ACL），因为百人企业配 ACL 会疯
- **注解 + AOP**：Handler 标 `@RequireRole` → AOP 切面自动拦截 `CommandPlugin.execute()` → 查 DB 校验。Handler 代码里不写 if-else，权限逻辑和业务逻辑分离
- **用户自动注册**：不要求用户填表单，首次发消息自动创建 bot_user 记录 + 分配 USER 角色。这和飞书的理念一致——飞书已经验证了身份，你只需要记录映射
- **第一个管理员手动指定**：新用户默认是 USER，SUPER_ADMIN 需要手动在 DB 里 INSERT bot_user_role。后续可做 `/promote` 指令

---

## 2026-05-18 — 功能 7：/search-ai AI 阅读理解

- `AiClient.java`：DeepSeek API 客户端（OpenAI 兼容接口）
- `SearchAiHandler.java`：检索文档 + 拼 Prompt + 调 AI + 回复
- 配置：`application-local.yml` 填 `ai.api-key`

---

## 2026-05-18 — 功能 6：/search 文档搜索

- `BotKnowledgeDoc.java`：知识库文档 Entity
- `BotKnowledgeDocMapper.java`：模糊搜索 Mapper
- `SearchHandler.java`：/search 指令处理器
- MySQL 插入 spec.md、database-design.md 摘要作为测试数据

---

## 2026-05-17 — 功能 5：/schedule 创建日程（首次写数据库）

- `BotSchedule.java`：日程 Entity（infrastructure）
- `BotScheduleMapper.java`：MyBatis-Plus Mapper
- `TimeParser.java`：5 个正则解析自然语言时间（common）
- `ScheduleHandler.java`：指令处理器（core）
- **首次使用 MyBatis-Plus 写入 MySQL**

---

## 2026-05-17 — 功能 4：/group 一键拉群

- **注意**：需在飞书开放平台开通 `im:chat` 权限才能使用
- `FeishuClient.createChat()`：调飞书创建群 API

---

## 2026-05-17 — 翻译 API 切换 & 环境修复

- **TranslateClient**：Google → MyMemory（国内可访问）
- **RabbitMQ** 暂时禁用（`application.yml` 排除 `RabbitAutoConfiguration`）
- 清理 `FeishuBotConnector`（WebSocket 长连接，统一走 Webhook）
- **bug**：MyMemory 返回 403，待解决

---

## 2026-05-16 — 功能 3：/translate 翻译指令

### 新增文件

| 模块 | 文件 | 说明 |
|------|------|------|
| integration/client | `TranslateClient.java` | Google 公共翻译端点客户端（免费免注册） |
| core/handler | `TranslateHandler.java` | `/translate` 指令处理器 |

### 设计决策

- 使用 Google 公共翻译端点 `translate.googleapis.com`，**免费、无需 API Key**，和天气 API 一样 GET 请求直接调
- 支持 10 种目标语言（中英日韩法德西俄葡意），源语言自动检测
- 用法：`/translate Hello`（默认译中文）、`/translate 你好 to 英文`

---

## 2026-05-16 — 功能 2：/help 指令 + 学习资料整理

### /help 指令

| 模块 | 文件 | 说明 |
|------|------|------|
| core/handler | `HelpHandler.java` | `/help` 指令处理器 |

### 设计决策

- **直接注入 `List<CommandPlugin>`** 而非 `CommandRouter`，避免循环依赖（HelpHandler → CommandRouter → List\<CommandPlugin\> 包含 HelpHandler 自身）
- `/help` 遍历所有已注册插件，自动生成指令菜单，新增指令后无需修改

### 清理

- 删除 `FeishuBotConnector.java`（WebSocket 长连接），项目统一走 Webhook 模式
- `application.yml` 移除 `connection-mode` 配置

### QS.md 学习资料新增

- Q5: /weather 完整实现链路讲解
- Q6: Webhook 概念与原理
- Q7: 接收 Webhook 标准流程
- Q8: RestTemplate 调用第三方 API 实践
- Q9: 异常处理与优雅降级
- Q10: /help 指令实现详解

---

## 2026-05-15 — 功能 1：/weather 天气查询（MVP 闭环）

### 新增文件（10 个 Java 文件 + 2 个配置）

| 模块 | 文件 | 说明 |
|------|------|------|
| common/dto | `CommandContext.java` | 指令上下文 DTO，含 `parse()` 方法解析 `/weather 北京` |
| plugin | `CommandPlugin.java` | 指令插件接口（name/description/execute） |
| api/dto | `WebhookEvent.java` | 飞书事件体 DTO，含嵌套类 Sender/Message |
| auth | `SignatureVerifier.java` | **HMAC-SHA256 签名验证器，含详细注释** |
| integration/dto | `WeatherResponse.java` | 和风天气返回体 DTO |
| integration/client | `WeatherClient.java` | 和风天气 API 客户端，内置 20 个城市映射 |
| integration/client | `FeishuClient.java` | 飞书 API 客户端（换 Token + 发消息） |
| core/router | `CommandRouter.java` | 指令路由器，自动发现所有 CommandPlugin |
| core/handler | `WeatherHandler.java` | `/weather` 指令处理器 |
| api/controller | `WebhookController.java` | Webhook 端点（GET=URL验证, POST=接收事件） |

### 配置文件变更
- **application.yml**：天气 Key 改用 `${QWEATHER_API_KEY:}` 引用，不再硬编码
- **application-local.yml**（新增）：存放真实密钥，已加入 .gitignore
- **.gitignore**（新增）

### 设计决策
- 飞书 SDK 暂未使用，API 调用全用 RestTemplate 直连（简单可控）
- HMAC 签名验证加开关 `signature-verification-enabled: false`，encrypt_key 未配置时跳过
- 天气城市用硬编码 Map（20 个常用城市），后续可升级为城市搜索 API
- Webhook URL 验证已实现（GET /webhook/event?challenge=xxx 握手）

### 下一步
- 配置飞书开放平台事件订阅 → 填入回调 URL → 在群里 @机器人测试
- 实现 /help 指令

---

## 2026-05-15 — Maven 依赖修复 & 全模块编译通过

### 问题
1. `settings.xml` 阿里云镜像被注释后，Maven 本地缓存的 `_remote.repositories` 元数据仍指向 aliyun
2. Lark SDK 在 JFrog Artifactory 返回 409，仓库不可达
3. GitLab4J 6.0.0-rc5 未发布到 Maven Central

### 解决方案
- **父 POM** 显式添加 `<repositories>` 指向 Maven Central，覆盖缓存中的阿里云地址
- **Lark SDK** 临时注释（飞书 API 改用 RestTemplate 手动封装，无需官方 SDK）
- **GitLab4J** 临时注释（Git 集成阶段再处理）
- **集成模块 POM** 同步注释相应依赖

### 结果：7/7 模块编译通过 ✅

---

## 2026-05-15 — 首轮代码：启动闭环

### 修复
- **Maven settings.xml**：注释掉阿里云镜像，VPN 新加坡节点无法访问 aliyun.com，改走 Maven Central

### 新增文件
| 模块 | 类 | 路径 | 说明 |
|------|-----|------|------|
| common | `ApiResult<T>` | `common/ApiResult.java` | 统一响应体，含 `success()` / `error()` 静态工厂 |
| common | `BizException` | `common/BizException.java` | 业务异常基类，含错误码 `code` 字段 |
| api | `FeishuBotApplication` | `api/FeishuBotApplication.java` | Spring Boot 主启动类，扫描 `com.bluemountain.bot` |

### 设计决策
- `ApiResult` 使用链式静态方法 `ApiResult.success(data)` 而非 Builder，保持与苍穹外卖项目习惯一致
- `BizException` 继承 `RuntimeException`，全局异常处理器可统一捕获并转为 `ApiResult` 返回
- 启动类放在 `api` 模块，`scanBasePackages = "com.bluemountain.bot"` 确保跨模块扫描所有 Bean

---

## 2026-05-15 — 配置校验与修复

### 修复内容
- **application.yml**：Redis `password` 字段补填 `123456`（与 docker-compose Redis 密码一致，之前为空会导致 NOAUTH 错误）
- **application.yml**：飞书 `app-secret` 环境变量名从 `${FEISHU_APP-_SECRET}` 修正为 `${FEISHU_APP_SECRET}`（去除了多余的 `-`）
- **docker-compose.yml**：MySQL root 密码末尾去除多余的 `/`

---

## 2026-05-15 — 环境配置修正

### 变更内容
- **docker-compose.yml**：MySQL 镜像从 8.0 降级为 5.7（适配开发机已有 MySQL 版本）
- **docker-compose.yml**：MySQL 端口映射从 `3306:3306` 改为 `3307:3306`（主机 3307 → 容器 3306）
- **application.yml**：数据库连接 URL 端口从 3306 改为 3307

---

## 2026-05-15 — 项目初始化

### 完成内容
- **项目骨架搭建**：Maven 多模块项目创建完毕，共 7 个子模块
- **父 POM 配置**：统一定义 Spring Boot 3.3.6、Java 17、以及全部第三方依赖版本
- **模块依赖关系**：common → infrastructure/auth/plugin → integration → core → api（无循环依赖）
- **配置文件**：application.yml 含全部占位符，待填入真实密钥
- **Docker Compose**：MySQL 8.0 + Redis 7 + RabbitMQ 3.13 一键启动

### 目录结构
```
D:\feishu_super_bot\
├── pom.xml                              # 父 POM（依赖管理 + 模块声明）
├── docker-compose.yml                   # 本地开发中间件
├── CHANGELOG.md                         # 本文件
├── feishu-bot-common/                   # 公共基础模块
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/common/
├── feishu-bot-infrastructure/           # 基础设施层
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/infrastructure/
├── feishu-bot-auth/                     # 鉴权模块
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/auth/
├── feishu-bot-plugin/                   # 插件框架
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/plugin/
├── feishu-bot-integration/              # 外部集成
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/integration/
├── feishu-bot-core/                     # 核心业务
│   ├── pom.xml
│   └── src/main/java/com/bluemountain/bot/core/
└── feishu-bot-api/                      # API 入口
    ├── pom.xml
    └── src/main/java/com/bluemountain/bot/api/
```

### ⚠️ 需要你手动填写的内容
| 位置 | 配置项 | 说明 |
|------|--------|------|
| application.yml | spring.datasource.username/password | MySQL 连接信息 |
| application.yml | spring.data.redis.password | Redis 密码 |
| application.yml | spring.rabbitmq.username/password | RabbitMQ 登录 |
| application.yml | spring.ai.openai.* | 大模型 API Key + Base URL + 模型名 |
| application.yml | feishu.* | 飞书应用 App ID / App Secret / 加密密钥 |
| application.yml | integration.weather.* | 天气 API Key |
| application.yml | integration.translate.* | 翻译 API Key |
| application.yml | bot.admin-chat-id | 管理员群 ID |
| docker-compose.yml | MYSQL_ROOT_PASSWORD | MySQL root 密码 |
| docker-compose.yml | redis --requirepass | Redis 密码 |
| docker-compose.yml | RABBITMQ_DEFAULT_USER/PASS | RabbitMQ 账号 |

### 下一步计划
1. ~~编写 api 模块主启动类 `FeishuBotApplication`~~ ✅
2. 实现飞书 Webhook 验签
3. 打通第一条 `/ping` 指令闭环

---

## 2026-05-24 — 项目收尾：修复 4 个遗留问题

### 修复内容

| 问题 | 状态 | 改动 |
|------|:--:|------|
| 翻译指令始终乱码 | ✅ | TranslateClient 签名计算改用原始文本（之前错误地对 URL 编码后的文本做了 MD5） |
| RestTemplate 无超时配置 | ✅ | 5 个 Client 全部加 connectTimeout=5s / readTimeout=10~60s |
| bot_command_log 未写入 | ✅ | 新建 Entity + Mapper，WebhookController.processCommand() 写入执行日志 |
| /search-ai DNS 待修 | ✅ | 验证 AiClient 配置正确，base-url 和 api-key 由 application-local.yml 提供 |

### 关停决策

| 技术点 | 决定 | 理由 |
|--------|------|------|
| HMAC 验签 | 暂不开启 | 内部工具无攻击面，上线后配置 encrypt-key 再开 |
| RabbitMQ | 不启用 | 线程池 10 线程处理秒级指令够用，无慢任务 |
| 智能降噪 | 不做 | /review Prompt 限定 4 类问题已起到过滤效果 |
