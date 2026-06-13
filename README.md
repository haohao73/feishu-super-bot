# 飞书超级助手 (Feishu Super Bot)

<div align="center">

**基于飞书开放平台的企业级智能效率机器人**

> 演示视频: [百度网盘](https://pan.baidu.com/s/1-xS0u54ek0G8eCltjo0_1g?pwd=1111) 提取码: 1111
> 补充视频: [百度网盘](https://pan.baidu.com/s/1801FRyX9WU71dFOKNUgN-Q?pwd=1111) 提取码: 1111

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen?style=flat&logo=spring)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue?style=flat&logo=openjdk)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-7模块多模块-C71A36?style=flat&logo=apachemaven)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7+-4479A1?style=flat&logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis)](https://redis.io/)
[![DeepSeek](https://img.shields.io/badge/AI-DeepSeek-4B32C3?style=flat)](https://www.deepseek.com/)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat)]()

</div>

---

##  目录

1. [项目简介](#1-项目简介)
2. [核心功能模块](#2-核心功能模块)
   - [基础指令集](#21-基础指令集)
   - [企业效率集成](#22-企业效率集成)
   - [高级功能](#23-高级功能)
3. [技术架构](#3-技术架构)
4. [技术挑战与实现要点](#4-技术挑战与实现要点)
5. [快速开始](#5-快速开始)
6. [指令大全](#6-指令大全)
7. [Webhook 配置教程](#7-webhook-配置教程)
8. [开发指南](#8-开发指南)
9. [项目结构](#9-项目结构)
10. [配置说明](#10-配置说明)

---

## 1.项目简介

**飞书超级助手** 是一个基于 [飞书开放平台](https://open.feishu.cn/) 开发的企业级智能群机器人，深度集成 AI 大模型与第三方 API，通过自然语言指令全面提升团队协作效率与办公自动化水平。

### 核心特性

-  **AI 赋能**：集成 DeepSeek 大模型，支持智能代码审查、知识库问答、多轮对话意图理解
-  **插件化架构**：基于 `CommandPlugin` 接口的指令框架，新增指令零侵入，不改任何已有代码
-  **审批催办**：飞书审批事件实时监听 + 定时任务轮询，私聊 / 群聊三层兜底推送催办消息
-  **DevOps 集成**：Gitee API 全链路（提交日志、代码差异、PR 状态），Push Webhook 自动代码审查
-  **多轮对话**：Redis 上下文记忆 + AI 意图解析，支持自然语言连续对话，无需重复输入 `/` 前缀
-  **权限控制**：4 级 RBAC 模型（SUPER_ADMIN → ADMIN → USER → READONLY），AOP 切面统一鉴权
-  **异步处理**：Webhook 毫秒级快速响应，业务逻辑线程池异步执行，避免飞书超时重试
-  **群名智能映射**：自动采集群名 → chat_id 映射存 Redis，广播消息时用群名替代不可读 ID
-  **用户自动注册**：首次发消息自动入库，零配置即可使用所有公开指令
-  **OAuth 2.0 日历同步**：用户授权后，`/schedule` 自动同步日程到飞书日历

### 技术栈

| 分类 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.3.6 |
| **编程语言** | Java 17 |
| **数据库** | MySQL 5.7+ / MyBatis-Plus 3.5.9 |
| **缓存** | Redis 7 / Lettuce 连接池 / Redisson |
| **AI 能力** | DeepSeek（OpenAI 兼容接口） |
| **HTTP 客户端** | WebClient（Spring WebFlux 响应式） |
| **鉴权** | 飞书 HMAC-SHA256 签名验证 / OAuth 2.0 |
| **定时任务** | Spring `@Scheduled` |
| **构建工具** | Maven 多模块 |
| **内网穿透** | natapp |
| **工具库** | Hutool / Lombok / Jackson |

---

## 2.核心功能模块

### 2.1 基础指令集

提供日常办公所需的常用功能，所有成员均可使用。

| 指令 | 语法示例 | 功能描述 |
|------|---------|---------|
| `/help` | `/help` | 显示所有可用指令及用法说明 |
| `/weather` | `/weather 北京` | 查询城市实时天气（温度、体感、湿度、风向） |
| `/translate` | `/translate Hello` | 多语言翻译（支持 10 种语言，自动检测源语言） |
| `/schedule` | `/schedule 明天 15:00 项目评审` | 创建日程（支持自然语言时间，可同步飞书日历） |
| `/search` | `/search 部署方案` | 搜索知识库文档（标题 + 内容模糊匹配） |
| `/search-ai` | `/search-ai 为什么选MySQL` | AI 阅读理解知识库并回答问题，附参考文档链接 |
| `/group` | `/group 项目群 张三 李四` | 创建飞书群聊，支持按姓名或 open_id 拉人 |

### 2.2 企业效率集成

集成 Gitee DevOps 工具链与飞书审批系统.

#### 2.2.1 Gitee Git 集成

| 指令 | 语法示例 | 功能描述 |
|------|---------|---------|
| `/gitlog` | `/gitlog haohao73/feishu_bot` | 查看仓库最近 5 条提交记录 |
| `/gitdiff` | `/gitdiff haohao73/feishu_bot abc123` | 查看指定提交的代码差异 |
| `/mergestatus` | `/mergestatus haohao73/feishu_bot 1` | 查看 PR 合并状态（冲突检测、作者、分支） |
| `/review` | `/review` + 代码片段 | AI 代码审查（NPE / 资源泄漏 / 异常 / 线程安全） |

#### 2.2.2 自动代码审查 (Push Webhook)

Gitee 仓库 Push 事件 → Webhook 推送 → 自动获取 diff → DeepSeek AI 审查 → 结果推飞书群。

**审查维度**：
- ✅ 空指针风险检测
- ✅ 资源泄漏分析（流、连接未关闭）
- ✅ 异常处理规范性
- ✅ 线程安全问题
- ✅ 代码质量评分（总分 10）

**触发方式**：
- **手动触发**：`/review <代码片段>`
- **自动触发**：配置 Gitee Webhook 后，每次 `git push` 自动审查

#### 2.2.3 审批催办系统

飞书审批事件实时监听 + 定时任务轮询，确保审批不被遗漏。

**三层兜底推送策略**：

```
1. 私聊推送（bot_user 注册的 open_id）
   ↓ 失败
2. 私聊推送（任意已注册用户代理发送）
   ↓ 失败
3. 群聊推送（配置的 review 群兜底）
```

**核心机制**：
- 审批提交 / 通过 / 拒绝事件通过 Webhook 实时同步到数据库
- `@Scheduled(cron = "*/10 * * * * *")` 每 10 秒轮询待催办记录
- 单条审批最多催办 5 次，避免骚扰
- 支持跨应用 open_id 容错处理

### 2.3 高级功能

#### 2.3.1 多轮对话（上下文感知）

用户首次输入 `/` 指令后，后续可直接用自然语言继续对话，无需重复输入命令前缀。

**工作流程**：

```
用户: /weather 北京
机器人: 北京天气：晴，25°C

用户: 那明天呢？（无需 /weather 前缀）
机器人: 北京明天天气：多云，22°C

用户: 帮我翻译成英文
机器人: Beijing tomorrow: Cloudy, 22°C
```

**技术实现**：
- `DialogService` 维护 Redis 对话上下文（`dialog:{openId}`，TTL 10 分钟）
- 非 `/` 开头的消息 → 组装历史对话 + 可用指令列表 → AI 意图解析 → 还原为完整指令
- 最多保留 5 轮对话历史，超出自动滚动

#### 2.3.2 权限控制系统

基于 RBAC 模型的 4 级权限体系，AOP 切面零侵入鉴权。

| 角色 | 权限范围 |
|------|---------|
| **SUPER_ADMIN**（超级管理员） | 所有权限，可管理机器人 |
| **ADMIN**（管理员） | 可使用管理指令（如 `/broadcast`） |
| **USER**（普通用户） | 可使用所有公开指令（默认角色） |
| **READONLY**（只读用户） | 仅可查询，不可执行写操作 |

**实现方式**：
- `@RequireRole("ADMIN")` 注解标记在 Handler 类上
- `RoleCheckAspect` 环绕通知拦截所有 `CommandPlugin.execute()` 调用
- SUPER_ADMIN 自动通过所有权限检查
- 鉴权失败返回中文提示，不暴露技术细节

#### 2.3.3 群名智能映射 (GroupRegistry)

无需记忆 `oc_xxx` 格式的群 ID，直接用群名操作。

**工作流程**：
1. 每条消息到达 → `GroupRegistry.collect(chatId)` 自动采集群名存 Redis
2. `/broadcast 项目群 | 标题 | 内容` → 自动将"项目群"解析为 `oc_xxx`
3. Redis 双向映射：群名 → chat_id / chat_id → 群名，TTL 7 天

#### 2.3.4 用户自动注册

首次在群内 @机器人 发消息 → 自动创建 `bot_user` 记录 → 默认分配 `USER` 角色 → 即可使用所有公开指令。零管理成本。

#### 2.3.5 OAuth 2.0 日历同步

`/schedule` 创建日程时：
1. 检测用户是否已授权飞书日历
2. 未授权 → 返回授权链接，用户点击授权
3. 授权回调 → 交换 `user_access_token` → 存入 `bot_user_token` 表
4. 后续 `/schedule` 自动同步到飞书日历（1 小时时长）

#### 2.3.6 消息广播

`/broadcast [群名1,群名2,...] | 标题 | 内容`

- 需 ADMIN 及以上权限
- 支持群名或 chat_id 混合指定
- 不指定目标则默认发当前群
- 记录广播日志（成功数 / 失败数）

#### 2.3.7 指令执行日志

每次指令调用记录到 `bot_command_log` 表：
- 执行状态（成功 / 失败）
- 执行耗时
- 错误信息
- 调用者、群聊、时间

---

## 3.技术架构

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                       飞书开放平台                            │
│  (群聊消息 / @机器人 / 审批事件 / 日历回调)                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ Webhook (HTTPS)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    WebhookController                        │
│  • 毫秒级返回 200（避免飞书 3s 超时重试）                      │
│  • HMAC-SHA256 签名验证（防伪造请求）                         │
│  • V1.0 / V2.0 双版本事件格式兼容                             │
│  • url_verification 挑战自动应答                             │
└───────────────────────────┬─────────────────────────────────┘
                            │ 异步提交
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              线程池 (10 线程固定大小)                         │
│  • 用户自动注册 → 群名采集 → 指令路由                         │
│  • 多轮对话上下文匹配                                         │
│  • 指令执行日志记录                                           │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     CommandRouter                            │
│  • 自动发现所有 CommandPlugin Bean                            │
│  • 精确匹配 + 未匹配提示                                      │
│  • AOP 权限校验 (RoleCheckAspect)                            │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               CommandPlugin 实现 (12 个 Handler)             │
│  WeatherHandler  TranslateHandler  ScheduleHandler          │
│  HelpHandler     SearchHandler    SearchAiHandler           │
│  GroupHandler    ReviewHandler    BroadcastHandler           │
│  GitLogHandler   GitDiffHandler   MergeStatusHandler        │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Integration Layer                         │
│  FeishuClient  AiClient  GiteeClient                        │
│  WeatherClient  TranslateClient                             │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               Infrastructure Layer                           │
│  MySQL (MyBatis-Plus)  │  Redis (Lettuce)                   │
│  用户/角色/日志/日程/审批/知识库                               │
└─────────────────────────────────────────────────────────────┘
```

### 消息处理流程

```
用户 @机器人 发消息
    │
    ▼
WebhookController (毫秒级返回 200)
    │
    ▼ (异步线程池)
用户自动注册 → 群名自动采集
    │
    ├── 是 / 开头 ──→ CommandRouter 路由 ──→ AOP 权限校验 ──→ Handler 执行业务逻辑
    │
    └── 非 / 开头 ──→ DialogService 上下文匹配 ──→ AI 意图解析 ──→ 还原为指令
    │
    ▼
保存对话上下文至 Redis ──→ 记录执行日志 ──→ 返回结果给用户
```

### 项目模块结构

```
feishu_super_bot/
├── feishu-bot-common/          # 公共：DTO、工具类、异常定义
├── feishu-bot-api/             # 入口：Controller、启动类
├── feishu-bot-core/            # 核心：Handler、Router、Service
├── feishu-bot-integration/     # 集成：飞书/天气/翻译/AI/Gitee Client
├── feishu-bot-auth/            # 鉴权：签名验证、权限注解、AOP 切面
├── feishu-bot-plugin/          # 插件：CommandPlugin 接口定义
└── feishu-bot-infrastructure/  # 基础设施：Entity、Mapper、数据库
```

**依赖方向**：`api → core → integration → auth / infrastructure / plugin → common`

---

## 4.技术挑战与实现要点

### 4.1 Webhook 快速响应与异步处理

**挑战**：飞书要求 Webhook 在 3 秒内返回响应，超时会指数退避重试，高并发下重复处理风险高。

**解决方案**：
- **快速响应**：接收请求后立即返回 `{"ok": true}`，业务逻辑全部异步执行
- **签名验证**：实现飞书 HMAC-SHA256 签名算法（`timestamp + nonce + encryptKey + body`），防伪造 + 防重放（时间戳 1 小时内有效）
- **双版本兼容**：同时兼容飞书 V1.0（`header.event_type`）和 V2.0（`type: event_callback`）事件格式
- **线程池隔离**：10 线程固定大小线程池处理消息，3 线程池处理 Gitee Webhook

### 4.2 审批事件跨应用 open_id 兼容

**挑战**：飞书审批事件的 `open_id` 可能属于不同应用上下文（错误码 `99992361: open_id cross app`），直接用该 open_id 发私聊消息会失败。

**解决方案**——三层兜底策略：
1. **首选**：用 `bot_user` 表中已注册用户的 open_id 发私聊
2. **备选**：遍历所有已注册 `bot_user`，用任意有效 open_id 代理发送
3. **兜底**：发送到配置的群聊，确保审批人一定看到

### 4.3 多轮对话 AI 意图理解

**挑战**：用户习惯用自然语言追问，如果每次都要输入完整指令体验很差。

**解决方案**：
- Redis 存储对话上下文（`DialogContext`），包含最近 5 轮历史
- 非指令消息 → 组装 prompt（历史对话 + 可用指令列表）→ AI 输出完整指令 → 重新路由
- 10 分钟无活动自动清理上下文

### 4.4 插件化扩展框架

**挑战**：传统方式每加一个功能要改 Controller、改路由表、改配置文件，耦合严重。

**解决方案**——`CommandPlugin` 接口 + Spring 自动发现：
- 新增指令只需实现 `CommandPlugin` 接口 + `@Component`，重启自动注册
- `CommandRouter` 通过 `List<CommandPlugin>` 自动收集所有实现
- `/help` 自动展示新指令，零配置

```java
@Component
public class MyHandler implements CommandPlugin {
    public String name() { return "mycommand"; }
    public String description() { return "我的指令"; }
    public String execute(CommandContext ctx) { /* 业务逻辑 */ }
}
```

### 4.5 响应式 HTTP 客户端迁移

**挑战**：项目初期使用 RestTemplate（阻塞式），在高并发场景下线程阻塞严重。

**解决方案**：全量迁移至 WebClient（Spring WebFlux 响应式）：
- 5 个 Client 类、20+ 个方法全部重构
- 自动 JSON 反序列化（`bodyToMono(Class/ParameterizedTypeReference)`），消除手动 JSON 解析代码
- 利用 `WebClient.builder()` 统一配置超时、编解码器

---

## 5.快速开始

### 前置要求

- **JDK 17** 或更高版本
- **MySQL** 5.7 或更高版本
- **Redis** 6.0 或更高版本
- **Maven** 3.6 或更高版本
- **飞书开放平台** 企业自建应用（需 App ID 和 App Secret）
- **natapp** 或其他内网穿透工具（本地开发需要公网回调 URL）

### 5.1 克隆项目

```bash
git clone https://github.com/haohao73/feishu-super-bot.git
# 或
git clone https://gitee.com/haohao73/feishu_bot.git

cd feishu_super_bot
```

### 5.2 启动中间件

```bash
docker compose up -d
# 启动 MySQL (3307) 和 Redis (6379)
```

或使用本机已安装的 MySQL 和 Redis。

### 5.3 初始化数据库

```bash
# 执行建表脚本
mysql -u root -p < sql/init.sql

# 导入知识库测试数据（可选）
mysql -u root -p < sql/insert_knowledge.sql
```

### 5.4 配置密钥

在 `feishu-bot-api/src/main/resources/` 下创建 `application-local.yml`：

```yaml
feishu:
  app-secret: 你的飞书应用 Secret
  encrypt-key: 飞书事件订阅的 Encrypt Key（可选，未开加密则留空）

integration:
  translate:
    baidu:
      app-id: 百度翻译 APP ID
      secret-key: 百度翻译密钥

ai:
  api-key: 你的 DeepSeek API Key
  model: deepseek-chat
  base-url: https://api.deepseek.com

gitee:
  token: 你的 Gitee 个人访问令牌（可选，公开仓库可不填）
```

### 5.5 启动服务

```bash
cd feishu-bot-api
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8080`。

### 5.6 配置飞书开放平台

1. [飞书开放平台](https://open.feishu.cn/) → 创建企业自建应用
2. **事件订阅** → 请求网址：`http://你的natapp域名/webhook/event`
3. **权限管理** → 开通以下权限：
   - `im:message` — 发送消息
   - `im:chat` — 创建群聊、管理成员
   - `contact:contact.base:readonly` — 按姓名查 open_id
   - `calendar:calendar:write` — 同步日程到日历
   - `approval:approval` — 访问审批应用
4. **订阅事件**：
   - `im.message.receive_v1` — 接收消息
   - `approval_instance` — 审批实例状态变更
   - `leave_approval` — 请假审批事件
5. **发布应用** → 加入测试群

### 5.7 验证部署

```bash
# 健康检查 — 浏览器访问
http://localhost:8080/webhook/event

# 模拟飞书事件
curl -X POST http://localhost:8080/webhook/event \
  -H "Content-Type: application/json" \
  -d '{"type":"url_verification","challenge":"test123"}'
# 应返回: {"challenge":"test123"}
```

---

## 6.指令大全

### 6.1 权限说明

| 权限级别 | 标识 | 说明 |
|---------|------|------|
| **公开** | 🟢 | 所有用户可使用的指令 |
| **管理员** | 🔴 | 需要 ADMIN 或 SUPER_ADMIN 角色 |

### 6.2 完整指令表

| 指令 | 权限 | 功能描述 |
|------|------|---------|
| `/help` | 🟢 | 显示所有可用指令列表 |
| `/weather <城市>` | 🟢 | 查询实时天气（温度 / 体感 / 湿度 / 风向） |
| `/translate <文本> [to 语言]` | 🟢 | 多语言翻译，支持 10 种语言，自动检测源语言 |
| `/schedule <时间> <事件>` | 🟢 | 创建日程，支持自然语言时间，可同步飞书日历 |
| `/search <关键词>` | 🟢 | 搜索知识库文档（标题 + 内容模糊匹配） |
| `/search-ai <问题>` | 🟢 | AI 阅读理解知识库并回答，附参考文档链接 |
| `/group <群名> [成员...]` | 🟢 | 创建群聊，支持按姓名或 open_id 拉人 |
| `/review <代码片段>` | 🟢 | AI 代码审查（NPE / 资源泄漏 / 异常 / 线程安全） |
| `/gitlog <仓库路径>` | 🟢 | 查看仓库最近 5 条提交 |
| `/gitdiff <仓库路径> <sha>` | 🟢 | 查看提交代码差异 |
| `/mergestatus <仓库路径> <PR编号>` | 🟢 | 查看 PR 状态（合并状态 / 冲突 / 作者） |
| `/broadcast [群名] \| 标题 \| 内容` | 🔴 | 消息广播到指定群聊，需 ADMIN 及以上权限 |

### 6.3 系统功能（非指令触发）

| 功能 | 触发方式 | 说明 |
|------|---------|------|
| **多轮对话** | 非 `/` 开头消息 | AI 解析意图，自动还原为指令 |
| **自动代码审查** | Gitee Push Webhook | Push 代码 → AI 自动审查 → 推送飞书群 |
| **审批催办** | 审批事件 Webhook + 定时任务 | 三层兜底推送催办消息 |
| **用户自动注册** | 首次发消息 | 自动入库，默认 USER 角色 |
| **群名自动采集** | 每条消息 | chat_id → 群名映射存 Redis |
| **指令执行日志** | 每次指令调用 | 记录到 MySQL，含状态 / 耗时 / 错误 |

---

## 7.Webhook 配置教程

### 7.1 飞书 Webhook（消息 + 审批事件）

**端点**：`POST /webhook/event`

**配置位置**：飞书开放平台 → 事件订阅 → 请求网址

```
URL: http://feishubot.nat300.top/webhook/event  （或你的 natapp 域名）
```

**需订阅的事件**：

| 事件名 | 用途 |
|--------|------|
| `im.message.receive_v1` | 接收用户 @机器人的消息 |
| `approval_instance` | 审批实例状态变更（提交 / 通过 / 拒绝） |
| `leave_approval` | 请假审批事件 |

### 7.2 Gitee Webhook（Push 自动代码审查）

**端点**：`POST /webhook/gitee`

**配置位置**：Gitee 仓库 → 管理 → Webhooks → 添加

| 配置项 | 值 |
|--------|---|
| URL | `http://feishubot.nat300.top/webhook/gitee` |
| 事件 | Push |
| 密码 | 留空（当前未开验签） |

配置后每次 `git push`，Gitee 推送事件 → bot 自动获取 diff → AI 审查 → 结果发飞书群。

### 7.3 调试方法

```bash
# 1. curl 本地测试飞书事件
curl -X POST http://localhost:8080/webhook/event \
  -H "Content-Type: application/json" \
  -d '{"type":"event_callback","event":{"type":"im.message.receive_v1",...}}'

# 2. 检查 natapp 隧道
浏览器打开 http://你的natapp域名/webhook/event

# 3. 飞书平台测试按钮
飞书开放平台 → 事件订阅 → 点击"测试"，看控制台日志
```

---

## 8.开发指南

### 插件化指令框架：如何添加自定义指令

本项目采用 **插件化指令框架**，新增指令只需实现 `CommandPlugin` 接口并标注 `@Component`，无需修改任何已有代码。

#### 步骤 1：新建 Handler 类

在 `feishu-bot-core/src/main/java/.../handler/` 下新建类：

```java
package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.plugin.CommandPlugin;
import org.springframework.stereotype.Component;

@Component  // Spring 自动发现，CommandRouter 自动注册
public class MyHandler implements CommandPlugin {

    @Override
    public String name() {
        return "mycommand";  // 指令名，用户输入 /mycommand
    }

    @Override
    public String description() {
        return "我的指令 — 用法：/mycommand 参数";
    }

    @Override
    public String execute(CommandContext ctx) {
        String args = ctx.getArgs();       // 获取用户输入的参数
        String openId = ctx.getUserId();   // 获取调用者 open_id
        String chatId = ctx.getChatId();   // 获取当前群 chat_id

        // 你的业务逻辑
        return "回复内容（支持飞书 Markdown 格式）";
    }
}
```

#### 步骤 2（可选）：调用第三方 API

在 `feishu-bot-integration` 模块创建 Client 类：

```java
@Component
public class MyClient {
    private final WebClient webClient = WebClient.builder().build();

    public String callApi(String param) {
        return webClient.get()
                .uri("https://api.xxx.com?param=" + param)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
```

在 Handler 中注入 `MyClient` 即可调用。

#### 步骤 3（可选）：操作数据库

在 `feishu-bot-infrastructure` 模块创建 Entity + Mapper：

```java
@Data
@TableName("my_table")
public class MyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
}

@Mapper
public interface MyMapper extends BaseMapper<MyEntity> {
}
```

在 `sql/init.sql` 中添加建表语句。

#### 步骤 4：重启测试

重启服务后：
- `/help` 自动展示新指令
- `CommandRouter` 自动路由到你的 Handler
- **不需要改任何已有代码**

### 模块职责约定

| 模块 | 职责 | 放什么 |
|------|------|--------|
| `feishu-bot-plugin` | 接口定义 | `CommandPlugin` 接口 |
| `feishu-bot-core` | 业务逻辑 | Handler 实现、Service、Router |
| `feishu-bot-integration` | 外部 API | Client 类（调用飞书 / AI / Gitee 等） |
| `feishu-bot-infrastructure` | 数据访问 | Entity、Mapper |
| `feishu-bot-api` | 入口 | Controller、启动类 |
| `feishu-bot-auth` | 安全 | 签名验证、权限注解、AOP |
| `feishu-bot-common` | 公共 | DTO、工具类、异常 |

### 权限控制

给 Handler 添加 `@RequireRole` 注解即可启用权限控制：

```java
@Component
@RequireRole("ADMIN")  // 只有 ADMIN 和 SUPER_ADMIN 可以使用
public class BroadcastHandler implements CommandPlugin {
    // ...
}
```

不添加该注解则默认所有用户可访问。

---

## 9.项目结构

```
feishu_super_bot/
├── pom.xml                            # 父 POM（7 模块聚合）
├── docker-compose.yml                 # 本地开发中间件（MySQL + Redis）
├── README.md
├── sql/
│   ├── init.sql                       # 建表 + 角色 / 知识库初始数据
│   └── insert_knowledge.sql           # 知识库测试数据
│
├── feishu-bot-common/                 # 公共模块
│   └── src/main/java/.../common/
│       ├── dto/                        # CommandContext, WebhookEvent, WeatherResponse...
│       ├── util/                       # TimeParser, CommandContextParser
│       └── exception/                  # BusinessException
│
├── feishu-bot-api/                    # 入口模块
│   └── src/main/java/.../api/
│       ├── FeishuBotApplication.java   # Spring Boot 启动类
│       └── controller/
│           ├── WebhookController.java  # 飞书事件回调（消息 + 审批）
│           ├── GiteeWebhookController.java  # Gitee Push 回调
│           └── OAuthController.java    # OAuth 2.0 回调
│
├── feishu-bot-core/                   # 核心业务模块
│   └── src/main/java/.../core/
│       ├── handler/                    # 12 个 CommandPlugin 实现
│       ├── service/                    # ApprovalReminderService, DialogService...
│       ├── router/                     # CommandRouter
│       └── registry/                   # GroupRegistry
│
├── feishu-bot-integration/            # 外部集成模块
│   └── src/main/java/.../integration/
│       └── client/
│           ├── FeishuClient.java       # 飞书 Open API（12 个方法）
│           ├── AiClient.java           # DeepSeek AI
│           ├── GiteeClient.java        # Gitee API（4 个方法）
│           ├── WeatherClient.java       # Open-Meteo 天气
│           └── TranslateClient.java    # 百度翻译
│
├── feishu-bot-auth/                   # 鉴权模块
│   └── src/main/java/.../auth/
│       ├── annotation/                 # @RequireRole
│       ├── aspect/                     # RoleCheckAspect
│       └── verifier/                   # SignatureVerifier (HMAC-SHA256)
│
├── feishu-bot-plugin/                 # 插件接口模块
│   └── src/main/java/.../plugin/
│       └── CommandPlugin.java          # 核心接口
│
└── feishu-bot-infrastructure/         # 基础设施模块
    └── src/main/java/.../infrastructure/
        ├── entity/                     # BotUser, BotRole, BotApprovalReminder...
        └── mapper/                     # MyBatis-Plus Mapper 接口
```

**数据库表（9 张在用）**：
`bot_user`, `bot_role`, `bot_user_role`, `bot_command_log`, `bot_message_broadcast`, `bot_schedule`, `bot_approval_reminder`, `bot_knowledge_doc`, `bot_user_token`

---

## 10.配置说明

| 配置项 | 位置 | 说明 |
|--------|------|------|
| MySQL 连接 | `application.yml` + `application-local.yml` | 默认 `127.0.0.1:3307`，密码通过 `${MYSQL_PASSWORD}` 环境变量注入 |
| Redis 连接 | `application.yml` | 默认 `localhost:6379`，Lettuce 连接池 |
| 飞书 App Secret | `application-local.yml` | 飞书开放平台 → 凭证与基础信息 |
| 飞书 Encrypt Key | `application-local.yml` | 可选，启用消息加密时需要 |
| 百度翻译密钥 | `application-local.yml` | 注册百度翻译开放平台（免费版每月 200 万字符） |
| DeepSeek API Key | `application-local.yml` | DeepSeek 注册即送 500 万 token |
| Gitee Token | `application-local.yml` | 可选，公开仓库可不填 |
| 审查目标群 | `application.yml` | `feishu.review-chat-id`：自动代码审查结果推送目标 |
| 签名验证开关 | `application.yml` | `feishu.signature.enabled`：默认 `false`，生产环境务必开启 |

---
##  交付产物

<div align="center">

###  项目文档

</div>

| 文档 | 路径 | 说明 |
|------|------|------|
| **项目设计文档** | `spec.md` | 需求分析、技术选型、架构设计 |
| **数据库设计文档** | `database-design.md` | 完整表结构、字段说明、索引设计 |
| **Gitee 集成指南** | `Gitee集成与Git操作详解.md` | Gitee API 对接、Webhook 配置、Git 操作 |
| **演示脚本** | `飞书助手_演示脚本.md` | 答辩演示流程、时间分配、12 个功能演示 |

<div align="center">

###  演示资源

</div>

| 资源 | 链接 |
|------|------|
| **演示视频** | [百度网盘](https://pan.baidu.com/s/1fIJRGdR3MI4kdLn2kMRQGQ?pwd=1111) 提取码: `1111` |
| **补充视频** | [百度网盘](https://pan.baidu.com/s/1801FRyX9WU71dFOKNUgN-Q?pwd=1111) 提取码: `1111` |
| **飞书测试群二维码** | [百度网盘](https://pan.baidu.com/s/1iAv3rrTCbn9Nv-EgsdE2yg) 提取码: `1111` |

<div align="center">

###  在线地址

</div>

| 平台 | 地址 |
|------|------|
| **GitHub** | [https://github.com/haohao73/feishu-super-bot](https://github.com/haohao73/feishu-super-bot) |
| **Gitee** | [https://gitee.com/haohao73/feishu_bot](https://gitee.com/haohao73/feishu_bot) |
| **飞书测试群** | 扫描上方二维码加入 |