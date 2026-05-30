# 飞书超级助手 — 企业效率机器人

基于 Spring Boot 3 的飞书群机器人，集成天气查询、翻译、日程创建、知识库 AI 问答、代码审查、消息广播等功能，通过 7 模块 Maven 项目 + 插件化指令框架实现。


## 指令列表

| 指令 | 功能 | 权限 |
|------|------|------|
| `/help` | 显示所有可用指令 | 所有人 |
| `/weather <城市>` | 查询实时天气 | 所有人 |
| `/translate <文本> [to 语言]` | 多语种翻译 | 所有人 |
| `/group <群名>` | 一键创建飞书群聊 | 所有人 |
| `/schedule <时间> <事件>` | 创建日程并同步飞书日历 | 所有人（日历同步需 OAuth 授权） |
| `/search <关键词>` | 搜索知识库文档 | 所有人 |
| `/search-ai <问题>` | AI 阅读理解知识库并回答 | 所有人 |
| `/gitlog <仓库路径>` | 查看仓库最近提交 | 所有人 |
| `/gitdiff <仓库路径> <sha>` | 查看提交代码差异 | 所有人 |
| `/review <代码片段>` | AI 代码审查 | 所有人 |
| `/broadcast [群名] \| 标题 \| 内容` | 消息广播 | ADMIN 及以上 |

### 系统功能

- **多轮对话**：Redis 上下文记忆 + AI 意图解析,当开启第一轮指令后,后续可以不再输入标准斜杠指令,
  用户的意图被ai解析后将会自动变成标准指令
- **自动代码审查**：Gitee push Webhook 触发 AI 审查，结果自动推送飞书群
- **审批催办**：飞书审批事件监听 + 定时任务自动催办(设定为五分钟查看一次符合审批提醒标准的审批清单进行催办)
- **用户体系**：首次发消息自动注册 + 4 级角色权限
- **群名映射**：自动采集群名，广播时用群名替代 `oc_xxx` 这种不可读 ID

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.6, Maven 多模块 |
| 数据库 | MySQL 5.7+, MyBatis-Plus 3.5.9 |
| 缓存 | Redis 7, Redisson |
| AI | DeepSeek（OpenAI 兼容接口） |
| 工具 | Hutool, Lombok, RestTemplate |
| 鉴权 | 飞书 HMAC-SHA256 签名验证, OAuth 2.0 用户授权 |
| 定时任务 | Spring @Scheduled |
| 内网穿透 | natapp |

---

## 项目结构

```
feishu_super_bot/
├── pom.xml                          # 父 POM
├── docker-compose.yml               # 本地开发中间件
├── sql/
│   ├── init.sql                     # 建表 + 初始数据
│   └── insert_knowledge.sql         # 知识库测试数据
├── feishu-bot-common/               # 公共：DTO、工具类、异常
├── feishu-bot-api/                  # 入口：Controller、启动类
├── feishu-bot-core/                 # 核心：Handler、Router、Service
├── feishu-bot-integration/          # 集成：飞书/天气/翻译/AI Client
├── feishu-bot-auth/                 # 鉴权：签名验证、权限注解、AOP 切面
├── feishu-bot-plugin/               # 插件：CommandPlugin 接口
└── feishu-bot-infrastructure/       # 基础设施：Entity、Mapper
```

**依赖方向**：api → core → integration → auth/infrastructure/plugin → common

---

## 快速开始

### 1. 克隆项目

```bash
git clone https://gitee.com/haohao73/feishu_bot.git(gitee和github上均上传)
cd feishu_bot
```

### 2. 启动中间件

```bash
docker compose up -d 
```

或使用本机已安装的 MySQL 和 Redis。


### 3. 建表

执行 `sql/init.sql` 和 `sql/insert_knowledge.sql`。

### 4. 配置密钥

复制 `application-local.yml` 到 `feishu-bot-api/src/main/resources/`，填入：

```yaml
feishu:
  app-secret: 你的飞书应用 Secret
  encrypt-key: 飞书事件订阅的 Encrypt Key（可选）
  oauth:
    redirect-uri: http://你的natapp域名/oauth/callback

integration:
  translate:
    baidu:
      app-id: 百度翻译 APP ID
      secret-key: 百度翻译密钥

ai:
  api-key: 你的密钥
  model: deepseek-chat
  base-url: https://api.deepseek.com
```


### 5. 启动

```bash
cd feishu-bot-api
mvn spring-boot:run
```

### 6. 配置飞书开放平台

1. 飞书开放平台 → 创建企业自建应用
2. 事件订阅 → 配置回调 URL：`http://你的natapp域名/webhook/event`
3. 权限管理 → 开通 `im:message` `im:chat` `calendar:calendar` `approval:approval` 等权限
4. 发布应用 → 加入测试群

---

## 配置说明

| 配置项 | 位置 | 说明 |
|--------|------|------|
| MySQL 连接 | application.yml + local | 默认 `127.0.0.1:3307`，用户名/密码在 application-local.yml 里填（已通过 `${MYSQL_PASSWORD}` 占位） |
| Redis | application.yml | 默认 `localhost:6379` |
| 飞书应用信息 | application.yml + local | app-id 固定，app-secret 在 local 里填 |
| 百度翻译 | application-local.yml | 需注册百度翻译开放平台（免费版每月 200 万字符） |
| AI API Key | application-local.yml | DeepSeek 注册即送 500 万 token |
| Gitee Token | application-local.yml | 可选，不配也能用 /gitlog /gitdiff（公开仓库无需鉴权） |
| 审查目标群 | application.yml | feishu.review-chat-id |

---

## 添加自定义指令

1. 新建类 `XxxHandler.java`（放在 `feishu-bot-core/handler/`）
2. 实现 `CommandPlugin` 接口：
   - `name()` → 指令名（不含 `/`）
   - `description()` → 一句话说明
   - `execute(CommandContext ctx)` → 业务逻辑
3. 标 `@Component`

Spring 自动发现，CommandRouter 自动收录，`/help` 自动展示。不改任何已有代码。

---
