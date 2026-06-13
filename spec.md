# 飞书超级助手机器人 — 项目总体设计方案

## 文档信息
- **项目名称**：飞书超级助手机器人（Feishu Super Bot）
- **版本**：v1.0
- **日期**：2026-05-15
- **架构模式**：Spring Boot 3 单体应用

---

## 一、项目背景与目标

### 1.1 项目概述
面向企业内部使用的飞书效率机器人，集成日常办公指令、企业效率工具、Git 代码管理、自动代码审查、CI/CD 触发、JIRA 工单查询、系统监控等能力，成为研发团队的一站式飞书助手。

### 1.2 核心能力全景
| 模块 | 说明 |
|------|------|
| 基础指令集 | /weather、/schedule、/group、/search、/translate、/help |
| 企业效率集成 | 消息广播、审批提醒、会议预约、权限控制、多轮对话 |
| Git 集成 | /gitlog、/gitdiff、/createbranch、/mergestatus |
| 自动代码审查 | Webhook 触发 CR + /review 手动触发 |
| 开发效率工具 | /deploy、/jira、/monitor |
| 可扩展指令框架 | 插件化指令注册，配置文件/类扫描动态加载 |

---

## 二、技术选型 — Spring Boot 3 单体架构

### 2.1 架构决策：单体而非微服务

| 维度 | 单体架构（采用） | 微服务架构（不采用） |
|------|------------------|----------------------|
| **团队规模** | 单人/小团队开发维护，单体足够 | 需要多团队独立迭代 |
| **业务复杂度** | 企业内部工具，领域边界清晰但量级不大 | 超大规模、多业务域系统 |
| **运维成本** | 一个 Jar 包部署，一条 Docker 命令启动 | 需要 K8s、服务发现、配置中心、网关等 |
| **调试难度** | 本地一键启动，调试链路短 | 需要链路追踪、分布式日志聚合 |
| **数据库事务** | 天然 ACID，事务管理简单 | 需要分布式事务（Saga/TCC）引入大量复杂度 |
| **网络开销** | 进程内调用，零延迟 | 服务间 RPC 调用增加延迟和故障点 |
| **可演进性** | 模块化分包，未来可按需拆为微服务 | 开局过度设计，返工成本高 |

**结论**：本项目用户量上限为百人/千人员工，QPS 预计在两位数，单体架构以最低的运维成本和最高的开发效率交付全部功能。当未来出现明确的性能瓶颈或团队拆分需求时，可按模块边界平滑拆分为微服务。

### 2.2 单体架构下的模块隔离原则
- Maven 多模块项目，编译期强制边界
- 模块间通过接口（API 模块）通信，不直接依赖实现类
- 数据库表按模块前缀隔离（`bot_`、`git_`、`sched_` 等）

---

## 三、技术栈清单

### 3.1 核心框架层
| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.x | 应用框架，IoC、自动配置、Actuator 健康检查 |
| **Spring MVC** | 随 Boot | REST API + Webhook 接收层 |
| **Spring WebFlux** | 随 Boot | Webhook 异步高并发处理（与 MVC 并存，仅 event 端点使用） |
| **Spring Security** | 随 Boot | 接口鉴权、飞书 Token 校验 |
| **Spring AI** | 1.x | 统一大模型调用抽象层（代码审查、翻译、知识库搜索） |
| **Spring Scheduler** | 随 Boot | 定时任务（审批提醒轮询、日程提醒） |

### 3.2 数据与缓存层
| 技术 | 用途 |
|------|------|
| **MySQL** 8.x | 主数据库，存储所有业务数据 |
| **MyBatis-Plus** 3.x | ORM，简化 CRUD，分页插件，代码生成器 |
| **Redis** 7.x | 多轮对话上下文缓存、Webhook 幂等 Key、Rate Limiter、Session |
| **Redisson** | Redis 分布式锁（定时任务单例执行） |

### 3.3 消息与异步
| 技术 | 用途 |
|------|------|
| **RabbitMQ** | 异步任务队列：部署回调、审查结果推送、消息广播 |
| **Spring AMQP** | RabbitMQ 集成，消息序列化与路由 |

### 3.4 第三方集成
| 技术 | 用途 |
|------|------|
| **Lark SDK**（飞书官方） | 消息发送、日历 API、群组管理、审批 API、云文档搜索 |
| **JGit** | Git 操作（log、diff、branch） |
| **GitLab4J / GitHub API** | GitLab/GitHub 平台集成（MR 状态、Webhook 处理） |
| **Jenkins Client** | 触发 CI/CD 并轮询构建结果 |
| **JIRA REST Client** | JIRA 工单查询与创建 |
| **Prometheus Client** | 拉取服务健康状态与错误率指标 |

### 3.5 工具与基础设施
| 技术 | 用途 |
|------|------|
| **Maven** | 构建与依赖管理 |
| **Docker + Docker Compose** | 一键部署（Bot + MySQL + Redis + RabbitMQ） |
| **Nginx** | 反向代理 + HTTPS（飞书 Webhook 要求 HTTPS 回调） |
| **JWT**（jjwt） | 内部管理 API 的 Token 鉴权 |
| **Lombok** | 减少样板代码 |
| **MapStruct** | DTO ↔ Entity 转换 |
| **Hutool** | 通用工具类集合 |
| **Logback** | 日志框架 |

---

## 四、数据库选型 — MySQL

### 4.1 为什么不选其他数据库

| 候选 | 排除理由 |
|------|----------|
| **PostgreSQL** | 功能同样优秀，但企业运维团队对 MySQL 经验更丰富；MyBatis-Plus 对 MySQL 的生态支持更成熟（分页方言、代码生成） |
| **MongoDB** | 本项目数据模型高度关系化（用户-角色-权限、日程-提醒-审批），关系型数据库更自然；多表关联查询频繁，文档模型并不适用 |
| **TiDB / OceanBase** | 分布式能力过剩，本项目百人级企业使用，单机 MySQL 完全够用 |
| **H2 / SQLite** | 仅适用于开发/测试，生产环境需完整 MySQL 功能与运维工具链 |

### 4.2 选择 MySQL 的核心原因
1. **生态成熟度**：Spring Boot + MyBatis-Plus 对 MySQL 的支持最完善，社区资源最丰富
2. **运维友好**：企业运维团队对 MySQL 备份、恢复、监控已有成熟流程
3. **ACID 事务**：审批流、日程创建、权限变更等场景要求强一致性
4. **与飞书数据对齐**：飞书内部的用户/部门/角色概念可直接映射到关系模型
5. **成本**：社区版免费，性能满足千级用户并发

---

## 五、项目模块划分

```
feishu-bot/
│
├── feishu-bot-common/          ← 公共基础模块
├── feishu-bot-api/             ← 对外接口层（Webhook + REST）
├── feishu-bot-core/            ← 核心业务逻辑
├── feishu-bot-integration/     ← 外部平台集成
├── feishu-bot-auth/            ← 鉴权与权限
├── feishu-bot-plugin/          ← 可扩展指令框架
├── feishu-bot-infrastructure/  ← 基础设施层
│
├── docker-compose.yml
├── Dockerfile
├── pom.xml                     ← 父 POM
└── README.md
```

### 5.1 各模块职责

#### `feishu-bot-common`（公共类）
- 通用异常类（`BizException`、`AuthException`）
- 统一响应体（`R<T>` / `ApiResult`）
- 常量定义（`RedisKey`、`MqQueue`、`ErrorCode`）
- 通用工具类（`JsonUtils`、`DateUtils`）
- 基础注解与切面
- **不依赖任何业务模块**，全部模块依赖它

#### `feishu-bot-api`（对外接口层）
- 飞书 Webhook 回调端点（接收消息事件、按钮交互、审批回调）
- 管理后台 REST API（指令统计、权限配置、机器人状态）
- OpenAPI 文档接口（Swagger/Knife4j）
- DTO 对象（请求/响应体的结构定义）
- 接口参数校验

#### `feishu-bot-core`（核心业务）
- 指令路由器（`CommandRouter`）：解析 `/xxx` 指令并分发到对应 Handler
- 多轮对话管理器（`DialogContextManager`）：基于 Redis 维护对话状态机
- 各指令处理器（`WeatherHandler`、`ScheduleHandler`、`GroupHandler` 等）
- 消息模板引擎（Markdown/MSG 格式卡片渲染）
- 服务层接口定义（面向 API 层的回包整合）

#### `feishu-bot-integration`（外部平台集成）
- **Git 集成**：JGit 操作（log/diff/branch）+ GitLab/GitHub API 客户端
- **代码审查**：Webhook 解析 → 变更提取 → 调用 AI 审查 → 结果格式化 → 发送飞书消息
- **CI/CD 集成**：Jenkins Client，触发构建 + 轮询状态 + 异步回调
- **JIRA 集成**：工单查询与创建
- **监控集成**：Prometheus HTTP API 拉取 + Grafana 内嵌面板链接
- **飞书 SDK 封装**：统一封装飞书消息/日历/审批/群组/文档 API 调用

#### `feishu-bot-auth`（鉴权与权限）
- 飞书 Webhook 签名校验（HMAC）
- 飞书用户身份解析（X-Lark-Request-Timestamp + X-Lark-Signature）
- 角色管理（超级管理员 / 管理员 / 普通用户 / 只读）
- 指令级权限控制（`@RequireRole` 注解 + AOP 拦截）
- 敏感操作二次确认（部署、创建分支等）

#### `feishu-bot-plugin`（可扩展指令框架）
- `CommandPlugin` 接口：`String name()` + `void execute(CommandContext)`
- SPI/Spring Bean 自动发现注册
- 支持外部配置文件定义简单指令（无需写代码，YAML 声明即生效）
- `/help` 动态菜单：扫描所有已注册插件自动生成帮助文本

#### `feishu-bot-infrastructure`（基础设施）
- 数据库配置（MyBatis-Plus 多数据源、分页拦截器）
- Redis 配置（序列化、连接池、Redisson）
- RabbitMQ 配置（队列/交换机/绑定声明、死信队列）
- 异步任务线程池（`@EnableAsync` + 自定义 `ThreadPoolTaskExecutor`）
- 全局异常处理（`@RestControllerAdvice`）
- 接口限流（Redis + AOP 实现令牌桶/滑动窗口）

---

## 六、模块依赖关系

```
                    ┌──────────────┐
                    │   common     │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  auth    │ │infra-    │ │ plugin   │
        │          │ │structure │ │          │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │            │            │
             └────────────┼────────────┘
                          │
                          ▼
                   ┌──────────────┐
                   │ integration  │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │    core      │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │     api      │
                   └──────────────┘
```

- **依赖链**：api → core → integration → auth / infrastructure / plugin → common
- **核心规则**：api 只与 core 交互；core 通过 integration 调用外部平台；auth 作为横切关注点，在各层通过注解生效
- **无循环依赖**，编译期通过 Maven 强制执行

---

## 七、关键技术决策说明

### 7.1 Webhook 高并发处理
- 飞书要求 3 秒内返回 200 OK，否则视为超时重试
- 策略：API 层收到 Webhook → 立即验签 → 返回 200 → 异步丢入 RabbitMQ → Core 层消费处理
- `Spring WebFlux` 提供 NIO 非阻塞能力，支撑 Webhook 端点的高吞吐

### 7.2 代码审查实现路径
1. GitLab/GitHub Webhook 推送 `push` 或 `merge_request` 事件
2. Integration 模块解析 Webhook → 提取 commit diff
3. 将 diff 封装为 Prompt → 调用 Spring AI（大模型）获取审查意见
4. 结果解析为结构化评分 + 问题列表 → 渲染为飞书卡片消息
5. 发送到配置的目标群/个人

### 7.3 扩展指令框架设计
```
指令注册方式：
  方式一：实现 CommandPlugin 接口 → Spring Bean 自动发现
  方式二：YAML 配置文件 → 启动时解析并注入 SimpleCommand Bean

/yaml 示例：
plugins:
  - name: "/ping"
    description: "检查机器人是否在线"
    handler: "com.example.plugin.PingHandler"
    roles: [USER, ADMIN]
    cooldown: 5s
```

### 7.4 安全性设计
| 层级 | 措施 |
|------|------|
| 传输层 | HTTPS（Nginx 终结 TLS） |
| 验签层 | 飞书 Webhook HMAC-SHA256 签名校验 |
| 认证层 | 飞书 Token 校验 + 内部管理 API 使用 JWT |
| 授权层 | 角色 + 指令级权限注解 |
| 操作层 | 敏感指令（/deploy、/createbranch）需二次确认 |

---

## 八、部署架构

```
                    ┌──────────┐
                    │  飞书用户  │
                    └────┬─────┘
                         │ HTTPS
                         ▼
                   ┌──────────┐
                   │  Nginx   │  ← TLS 终结 + 反向代理
                   └────┬─────┘
                         │
                         ▼
              ┌──────────────────┐
              │  feishu-bot.jar  │  ← Spring Boot 3 应用
              └───┬───┬───┬─────┘
                  │   │   │
         ┌────────┘   │   └────────┐
         ▼            ▼            ▼
   ┌─────────┐ ┌─────────┐ ┌──────────┐
   │  MySQL  │ │  Redis  │ │ RabbitMQ │
   └─────────┘ └─────────┘ └──────────┘
```

- 单机/单容器部署完整技术栈
- Docker Compose 一键启动：`docker compose up -d`
- 应用本身可横向扩展（无状态），加实例 + Nginx upstream 即可

---

## 九、我对这个项目的理解

这是一个**小而全**的企业内部效率工具。它的核心价值不在于某个单点功能的深度，而在于**聚合**——把开发团队日常在飞书、GitLab、JIRA、Jenkins、监控平台之间来回切换的碎片化操作，统一收敛到飞书聊天窗口里。

从工程角度，三个最有趣的技术挑战：
1. **多轮对话上下文管理**：用户说"帮我查张三的 JIRA 任务"→接着发"前天的天气呢"→再问"帮我也建一个"，机器人需要识别话题切换、参数继承与冲突消解。这需要一套基于 Redis 的对话状态机。
2. **代码审查的智能降噪**：Git diff 可能包含大量格式变更、import 重排等噪声，直接丢给大模型审查会浪费 Token 且冲淡真正的问题。需要在送审前做 diff 预处理和过滤。
3. **插件化指令框架**：要支持"不重启就加新指令"，本质是做一个微型的插件容器——指令发现、生命周期管理、热加载/卸载。这是架构设计中最考验扩展性的部分。

---

## 十、下一步

本设计方案评审通过后，进入以下阶段：
1. 搭建 Maven 多模块项目骨架
2. 数据库表结构设计
3. 实现 Webhook 验签 + 指令路由最小闭环（MVP）
4. 逐模块迭代交付

---

## 十一、模块划分的设计思考（给新手的复盘笔记）

### 写在前面

模块划分是架构中最容易被新手低估的事。很多人拿到需求就开写，一个 `service` 包塞 50 个类，写到后面自己都找不到代码。模块划分的本质是**给代码划边界**——什么和什么属于一起，什么和什么永远不该直接说话。

下面我把每个模块的设计理由掰开来讲。

---

### 11.1 `common` — 为什么需要一个"什么都不是"的模块？

新手常问："放一个 common 包不就是为了好看吗？我直接在每个模块里写自己的工具类不行吗？"

不行。原因很简单：**没有公共层，就会出现相同的代码写 N 遍**。

比如统一响应体 `ApiResult<T>`。api 模块返回给飞书要用它，core 模块内部流转也要用它，integration 模块回调也要用它。如果每个模块各自定义一个 `ApiResult`，你改一个字段就要改三个地方——而且你一定会忘。

common 模块的核心纪律就一条：**只能放没有业务含义的东西**。工具类、异常定义、常量、基础注解——这些东西和业务无关，任何模块都需要。一旦你在 common 里写了和具体业务有关的代码（比如放了个 `ScheduleEntity`），它就变成了所有模块的耦合点，改 schedule 逻辑居然要去动 common，边界就崩了。

判断一个东西能不能进 common 的方法：**想象你把这个类复制到另一个完全不同的项目里，它还能用吗？** 能，就可以进。不能，说明它有业务属性，不该放 common。

---

### 11.2 `api` 和 `core` 为什么要分开？

这可能是最重要的一刀。很多小项目都会写成这样：

```
Controller ──→ Service ──→ Mapper
```

三层全在一个模块里，看起来简单，但有一个致命问题：**你的核心逻辑和通信协议绑定死了**。

飞书机器人的 API 层接收的是飞书 Webhook 格式（JSON 里的 `event.message.content`），但你的 core 模块不应该知道"消息是从飞书来的还是从企业微信来的还是命令行来的"。core 应该处理标准的 `CommandRequest` 对象，由 api 层负责把飞书的 JSON 转成这个对象。

分开的好处：
- **协议无关**：哪天老板说"机器人也要接钉钉"，你只需要在 api 模块新增一个钉钉的 Controller，core 里的指令处理逻辑一行不动
- **可测试性**：你可以不启动 HTTP 服务就直接写单元测试测 core 的逻辑，构造一个 `CommandRequest` 对象丢进去就行
- **版本隔离**：api 的 DTO 随外部接口变化而频繁改动（飞书 API 升级了），但 core 保持稳定。你不会因为飞书改了个字段名就要改核心业务代码

一句话总结：**api 是翻译官，core 是决策者。翻译官可以换，决策者不该动。**

---

### 11.3 `integration` — 凭什么第三方集成要单独一个模块？

integration 模块的存在理由：**隔离外部不稳定因素**。

GitLab API 今天用 v4，明天升级 v5；Jenkins 的认证方式从用户名密码变成 Token；Prometheus 的查询语法从 1.x 变 2.x——这些变化和你的业务逻辑没有任何关系，但如果你的代码审查逻辑直接调 GitLab API，每次外部变更都会污染你的核心代码。

integration 模块的纪律：
- 每个外部平台一个子包（`git`、`jira`、`jenkins`、`monitor`）
- 对外暴露接口（如 `GitService`），内部封装 SDK 调用细节
- core 模块只依赖接口，不知道也不该知道底层用的是 GitLab 还是 GitHub

这本质上就是**适配器模式**在全模块层面的应用。当你需要换一个外部平台时，你只需要在 integration 里写一个新的实现类，core 无感知。

---

### 11.4 `auth` — 鉴权为什么值得独立出来？

很多人把鉴权逻辑写在 Controller 的 Filter 或 Interceptor 里就完事了。但对于这个项目，auth 单独成模块有三个原因：

1. **跨层渗透**：鉴权不只是 API 层的活。某些敏感指令（/deploy、/createbranch）需要二次确认，这是在 core 层发生的。auth 模块提供的注解（`@RequireRole`、`@Sensitive`）需要能在 core 层被 AOP 拦截
2. **多种鉴权源**：飞书 Webhook 验证 HMAC 签名、管理 API 验证 JWT Token、部分接口可能用 API Key。三种鉴权方式共享同一套角色模型，如果不独立出来，会在各层重复写验证逻辑
3. **未来可替换**：如果你们公司从飞书切到其他平台，auth 模块整体替换，不影响其他模块

**判断标准**：如果一段鉴权逻辑散落在三个以上的模块里出现，它就需要集中到一个地方。

---

### 11.5 `plugin` — 最容易被跳过的模块，也最关键

很多有经验的人做企业机器人，指令都是硬编码的——一个巨大的 switch-case 或者 if-else 链。项目初期有 6 个指令，写 switch 很爽；半年后加到 30 个指令，那个 switch 就变成了没人敢碰的屎山。

plugin 模块要解决的核心问题是：**新增一个指令时，只新增代码，不修改已有代码**。这是开闭原则（OCP）在项目层面的落地。

plugin 提供 `CommandPlugin` 接口，每个指令是一个独立的实现类。Spring 自动发现所有实现类并注入到指令路由器。加新指令 = 新建一个类 + 实现接口，其他代码零改动。

更深一层：plugin 模块是**团队协作的边界线**。如果一个项目有 3 个人同时开发不同指令，在 plugin 架构下他们永远不会改同一个文件（没有冲突）。在 switch-case 架构下，他们天天在同一个 switch 里打架。

---

### 11.6 `infrastructure` — 基础设施为什么是一个模块而不是配置文件？

Spring Boot 的 `application.yml` 已经能做很多事（数据库连接、Redis 地址、MQ 配置），为什么还要单独建一个 infrastructure 模块？

因为**配置 ≠ 基础设施**。基础设施模块放的是：
- 自定义的配置类（比如动态数据源路由、多 Redis 模板封装）
- 全局行为定义（`@RestControllerAdvice` 异常处理器、请求日志拦截器、全局限流切面）
- 中间件的 Java 层面封装（消息转换器、序列化策略、死信处理器）

这些东西的共同特征：**它们不体现业务，但它们决定了应用的整体运行方式**。把基础设施逻辑和业务代码混在一起，会让项目难以理解——你翻到一个 service 类，里面一半代码在处理 MQ 重试逻辑，一半在写业务，心智负担极高。

---

### 11.7 模块划分的总原则

回过头看，这个 7 模块的划分遵循了同一个逻辑链条：

```
越底层 → 越稳定 → 越不依赖业务
越上层 → 越易变 → 越贴近具体需求

common     ← 万年底座
  ↑
auth / infra / plugin  ← 架构骨架
  ↑
integration  ← 外部适配
  ↑
core         ← 业务核心
  ↑
api          ← 对外接口（最易变）
```

**一个好的架构，改动成本最高的模块应该最稳定。** 在这个结构里，common 几乎不改，auth 和 infra 偶尔改，core 经常加新指令但不改旧代码，api 随便改 DTO 和返回格式。

如果你发现自己在一个稳定的底层模块里频繁改代码，那说明边界画错了——有些东西不应该在那个模块里。

---

### 11.8 给你的建议（作为第一个独立项目）

1. **不要一开始就追求完美**。模块边界是演进出来的，不是设计出来的。Maven 多模块可以先建，但发现某个模块一直空着或者只有一两个类，就果断合并。我见过太多项目一开始分了 15 个模块，最后 8 个只放了一个类。

2. **依赖方向错了就重构**。如果你发现 common 依赖了 core（编译报错），说明你把不该放 common 的东西放下去了。Maven 的编译期隔离是最诚实的——它不会骗你。

3. **命名比你想的更重要**。好的模块名本身就是文档。`feishu-bot-core` 这个名字告诉新加入的人："从这里开始看"。`feishu-bot-integration` 告诉他："如果你只想改 JIRA 相关的东西，在这里改"。

4. **享受这个过程**。独立开发一个完整项目，从架构设计到上线运行，是你成长最快的阶段。你会在这个项目里犯的每一个错误，都会成为以后你不会再犯的经验。

---

## 十二、为什么"对得上又对不上"——苍穹外卖到飞书机器人的断层在哪

### 12.1 先说你已经会的东西（它们没变）

苍穹外卖和黑马商城教会你的，是这个项目的**底层骨架**：

```
请求进来 → Controller 接收 → Service 处理 → Mapper 读写 → 返回响应
```

这个骨架在飞书机器人项目里**一毫米都没变**。数据库设计你理得清楚，就是因为表就是表，和你之前写的 `dish` 表、`order` 表是一样的东西——字段、索引、外键，全是一个套路。

你现在会的，在这个项目里的覆盖率：

| 苍穹外卖你掌握的 | 在本项目的对应 | 能用吗 |
|---|---|---|
| Controller 接收请求 | Webhook Controller 接收飞书事件 | 一模一样，换个包名 |
| Service 层业务逻辑 | 指令处理器（Handler） | 写法完全一样 |
| MyBatis-Plus CRUD | 操作 `bot_user`、`bot_schedule` 等表 | 零差别 |
| Redis 缓存 | 存对话上下文、接口限流 | 一样的 API，换个 key |
| Spring 注解（`@Autowired` 等） | 完全一样 | 100% 复用 |
| Maven 项目结构 | 多了几个子模块 | 父 POM 写法一样 |

**你的痛苦不在底层，在应用层。**

---

### 12.2 让你别扭的三个核心差异

#### 差异一：请求从"我主动调用"变成"别人推送给我"

苍穹外卖的请求流是你熟悉的"一问一答"：

```
小程序 ──发起请求──→ 你的服务器 ──返回 JSON──→ 小程序
用户主动操作          你处理                用户看到结果
```

飞书机器人的请求流是**事件推送**：

```
用户在飞书群发消息 ──→ 飞书服务器 ──推送事件到你的服务器──→ 你处理──→ 调飞书 API 发消息回去
                      （不是你请求飞书，是飞书请求你）
```

**你在苍穹外卖里写的是"等别人来调我"，在飞书里写的也是"等别人来调我"**——所以底层是一样的。那为什么感觉不对？

因为苍穹外卖里，调你的是你自己写的小程序，你知道请求长什么样。飞书调你的时候，请求体是飞书定义的 `event` 结构，你只能去翻飞书文档——这是你第一次被外部平台定义的数据格式"入侵"自己的代码，所以觉得失控。

**本质上还是 Controller 接收 JSON，只是 JSON 结构不是你设计的，是飞书设计的。**

---

#### 差异二：鉴权从 JWT 变成了 HMAC 签名验证

苍穹外卖：

```
用户登录 → 你发 JWT Token → 用户每次请求带 Token → 你解析 Token 获取用户 ID
```

飞书机器人：

```
飞书推送消息 → 飞书在 HTTP Header 里带 Timestamp + Signature
           → 你用 HMAC-SHA256( Timestamp + 你的AppSecret ) 算出签名
           → 比较算出来的和飞书发来的是否一致
           → 一致 = 消息确实来自飞书，不是伪造的
```

两者的共同点：都是**验证请求发起者是不是合法的**。区别在于：

- JWT 是你自己签发自己验证，密钥是你的
- HMAC 是飞书用它的密钥签名，你用飞书给你的 AppSecret 验签

代码量差不多，都是写一个拦截器/过滤器。**不要让"HMAC"三个字吓到你，它就是一个 IF 判断。**

```java
// JWT 校验（你熟悉的）
String userId = jwtUtils.parseToken(token);

// 飞书 HMAC 校验（你要写的，其实也就一行）
boolean valid = SignatureVerifier.verify(timestamp, nonce, body, appSecret);
```

---

#### 差异三：响应从"return JSON"变成了"调 API 发消息"

这是最让你感觉"不对"的地方。

苍穹外卖里，Controller 方法的最后一行是这样的：

```java
return Result.success(orderList);  // 返回 JSON，框架帮你序列化，响应就结束了
```

飞书机器人里，Webhook 的 Controller 是这样的：

```java
// 第一步：立刻返回 200（飞书要求 3 秒内响应，否则会重试）
return Result.success(null);  // 这一步和苍穹外卖一样

// 第二步：真正的处理异步执行
// 调飞书 API：POST https://open.feishu.cn/open-apis/im/v1/messages/...
// 把回复消息发回群里
```

你写苍穹外卖时，"返回数据给用户"这件事 Spring MVC 帮你做了（return 就是响应）。但在飞书机器人里，**Webhook 的响应只是"我收到了"，真正的回复要调飞书的发送消息 API**。

这导致一个新手很容易困惑的事实：处理一条消息 = 两个 HTTP 请求（接收一个 + 发送一个）。

但如果你拆开看：
- "接收 Webhook" = 一个 Controller 方法（你写过一万次了）
- "发送消息" = 调一个 HTTP API，用 RestTemplate 或 HttpClient（你在苍穹外卖里调微信支付也是这么干的）

**两个操作你都会，只是之前没把它们组合在一起过。**

---

### 12.3 用一张图把新旧知识对齐

```
苍穹外卖处理"用户下单"                    飞书机器人处理"用户发 /weather 北京"
─────────────────────────                ───────────────────────────────────
                                         
① 小程序发 POST /order/submit            ① 飞书服务器发 POST /webhook/event
   Body: {dishId:3, quantity:2}             Body: {event:{message:{text:"/weather 北京"}}}
        │                                         │
        ▼                                         ▼
② Controller 接收 JSON                    ② Controller 接收 JSON
   @PostMapping("/submit")                  @PostMapping("/event")
   public Result submit(@RequestBody)       public Result onEvent(@RequestBody)
        │                                         │
        ▼                                         ▼
③ 验 JWT Token                           ③ 验飞书 HMAC 签名
   jwtUtils.parseToken(token)               verifySign(timestamp, body, secret)
        │                                         │
        ▼                                         ▼
④ Service 处理业务                        ④ 解析指令 → 调天气 API
   orderService.submit(dishId, qty)          weatherService.query("北京")
        │                                         │
        ▼                                         ▼
⑤ Mapper 写入数据库                       ⑤ 构建消息卡片 → 调飞书发送 API
   orderMapper.insert(order)                 feishuClient.sendMessage(chatId, card)
        │                                         │
        ▼                                         ▼
⑥ return Result.success(orderVO)          ⑥ return Result.success(null)  ← 3秒内响应
```

五步一样，多出来的第⑤步"调飞书 API 发消息"——它跟你调微信支付接口一样，就是一个 HTTP POST。

---

### 12.4 给你拨开迷雾

你现在最核心的不适感来源是：

> 苍穹外卖是一个"教程项目"，所有设计决策已经有人替你做了，你执行就行。这个项目是一个"工程任务"，你要自己做设计决策，同时还要学新技术。两件事叠在一起，产生了一种"我好像什么都不会"的错觉。

但实际上：

- **数据库设计**你已经理清了（16 张表清清楚楚）
- **模块划分**你已经理解了（7 个模块各司其职）
- **底层 CRUD** 你闭着眼都能写
- **飞书 API** 只是一个 HTTP 调用，和微信支付接口没有本质区别
- **HMAC 验签**只是一个 IF 判断，和 JWT 解析没有本质区别
- **异步处理**你用 RabbitMQ 就行了，Spring AMQP 的 `@RabbitListener` 和 `@RestController` 写法几乎一样

你现在需要的是一个具体的、可执行的下一步。

---

### 12.5 现在该干什么

我们已经完成了：
1. 需求分析 → 模块划分（spec.md）
2. 数据库设计（database-design.md）

按顺序，下一步是：

**搭建 Maven 多模块项目骨架 + 打通第一条指令。**

具体来说：
1. 用 Maven 建父 POM + 7 个子模块，确保 `mvn compile` 能过
2. 在 api 模块写一个飞书 Webhook 端点（`/webhook/event`）
3. 写飞书 HMAC 验签过滤器
4. 实现 `/ping` 指令——用户发 `/ping`，机器人回 `pong`
5. Docker Compose 拉 MySQL + Redis + RabbitMQ

做完这 5 步，你就能在飞书群里看到机器人真的回复你了。那个瞬间你会明白——**原来就是 Controller → Service → 调 API，和苍穹外卖是一样的活。**

---

### 12.6 最后一句

你组长说得完全对——**底层还是后端那一套**。

你只是从"写接口给别人调"变成了"别人调我的接口，我再调别人的接口"。中间多了一个环节，但每个环节的写法，都是你在苍穹外卖里练过的东西。

你现在的感觉，不是"我不会"，是"没人告诉我下一步该干什么"。

这就是文档的作用。接下来你只需要按步骤执行，走完第一个指令闭环，迷雾就散了。
