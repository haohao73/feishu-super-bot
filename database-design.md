# 飞书超级助手机器人 — 数据库设计文档

## 一、拿到需求后怎么推导出表结构？（方法论）

苍穹外卖的数据库是别人设计好给你的，你只需要"用"。现在你要"设计"，核心能力是**从需求倒推出表**。

### 我的三步推导法

```
第一步：找出所有"名词" → 这些是候选表
第二步：找出名词之间的"关系" → 这些是外键/关联表
第三步：找出每个名词需要记录的"属性" → 这些是字段
```

举个例子，从需求文档里读这一句：

> "/schedule <时间> <事件> ‒ 创建个人日程"

- **名词**：用户、日程
- **关系**：一个用户有多个日程（一对多）
- **属性**：日程需要时间、事件内容、谁创建的

推导结果：需要一张 `bot_schedule` 表，字段有 `user_id`、`schedule_time`、`event_content`。

就这么简单。下面我用这个方法把整个项目的表全推导出来。

---

## 二、从需求中提取所有名词

把需求文档扫一遍，把所有"实体名词"圈出来：

| 来源需求 | 提取的实体 |
|----------|-----------|
| 用户发指令 | **用户** |
| 权限控制：管理员/特定角色 | **角色** |
| /group 一键拉群 | **群组配置** |
| /schedule 创建日程 | **日程** |
| /weather 查天气 | **天气查询记录**（可选） |
| 消息广播 | **广播消息** |
| 审批提醒 | **审批提醒** |
| 会议预约 | **会议** |
| /search 知识库搜索 | **知识库文档** |
| /translate 翻译 | **翻译记录**（可选） |
| /gitlog <仓库别名> | **Git 仓库配置** |
| /deploy <环境> | **部署任务** |
| /jira <任务编号> | **JIRA 配置** |
| 代码审查结果 | **审查记录** |
| 对话上下文 | **对话会话**（Redis） |
| 指令执行 | **指令执行日志** |
| 自定义指令插件 | **插件配置** |

---

## 三、完整表结构设计

### 3.1 用户与权限（3 张表）

#### `bot_user` — 用户表

这张表是所有操作的起点。任何指令都需要知道"谁发的"。

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | 数据库基本要求 |
| `feishu_open_id` | VARCHAR(64) | 飞书用户唯一标识 | 飞书发来的消息里带的，用来识别"这个人是公司里的谁"。**唯一索引** |
| `feishu_union_id` | VARCHAR(64) | 飞书统一 ID | 跨应用的唯一标识，如果以后机器人接入多个飞书应用会用到 |
| `name` | VARCHAR(64) | 用户姓名 | 回复消息时用，比如"你好，张三" |
| `avatar_url` | VARCHAR(255) | 头像 URL | 消息卡片里展示头像 |
| `department` | VARCHAR(128) | 部门名称 | 权限判断用，比如"只有研发部能 /deploy" |
| `status` | TINYINT | 1=正常 0=禁用 | 离职员工的账号要禁用但不能删（历史记录还关联着） |
| `create_time` | DATETIME | 创建时间 | 审计用 |
| `update_time` | DATETIME | 更新时间 | 审计用 |

**设计思考**：用户表不存密码，因为飞书本身就是登录系统，我们信任飞书的身份。这和你做苍穹外卖时用户表存密码完全不同——**第三方平台接入的项目，身份由平台保证，你只记录映射**。

---

#### `bot_role` — 角色表

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `role_code` | VARCHAR(32) | 角色编码 | 如 `SUPER_ADMIN`、`ADMIN`、`USER`、`READONLY`。代码里用编码做判断，不用中文 |
| `role_name` | VARCHAR(32) | 角色显示名 | 如"超级管理员"，给管理员在后台看的 |
| `description` | VARCHAR(255) | 角色描述 | 说明这个角色能干什么 |
| `create_time` | DATETIME | 创建时间 | |

**设计思考**：为什么 `role_code` 用英文编码而不是中文？
```java
// 好：改了角色名代码不受影响
if (user.hasRole("SUPER_ADMIN")) { ... }

// 坏：运营把"超级管理员"改成"系统管理员"，代码就挂了
if (user.hasRole("超级管理员")) { ... }
```

---

#### `bot_user_role` — 用户角色关联表

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `user_id` | BIGINT | 用户 ID | 外键关联 `bot_user.id` |
| `role_id` | BIGINT | 角色 ID | 外键关联 `bot_role.id` |
| `create_time` | DATETIME | 授权时间 | |

**设计思考**：为什么不直接在 `bot_user` 里加一个 `role_id` 字段？

因为一个用户可能有多个角色。比如张三既是"管理员"又是"研发部成员"，如果只存一个 `role_id`，张三就只能有一个身份。中间表是多对多关系的标准解法。你在苍穹外卖里用户和收货地址是一对多（一个用户多个地址），这里用户和角色是多对多（一个用户多个角色，一个角色包含多个用户），所以需要第三张表来连接。

---

### 3.2 指令与日志（2 张表）

#### `bot_command_log` — 指令执行日志

**这就是你问的"用户发指令要不要存"——要，而且很重要。**

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `user_id` | BIGINT | 执行者 | 谁发的指令 |
| `command` | VARCHAR(64) | 指令名 | 如 `/deploy`、`/weather`，方便统计"哪个指令用得最多" |
| `raw_message` | TEXT | 原始消息全文 | 用户发什么就存什么，方便回溯问题 |
| `params` | JSON | 解析后的参数 | `{"city":"北京"}`，方便后续分析,MySQL 5.7+ 支持 JSON 类型 |
| `status` | TINYINT | 1=成功 2=失败 3=权限拒绝 4=执行中 | 统计成功率 |
| `error_msg` | VARCHAR(1024) | 错误信息 | 失败了知道为什么 |
| `chat_id` | VARCHAR(64) | 飞书群 ID | 记录从哪个群发起的，排查问题时能定位 |
| `execute_time_ms` | INT | 执行耗时(毫秒) | 性能监控，"这个指令最近变慢了" |
| `create_time` | DATETIME | 执行时间 | |

**设计思考**：这张表是运维的生命线。有人跟组长投诉"前几天我发了 /deploy 但没反应"，你打开这张表一查就知道到底发生了什么。没有这张表，你只能靠猜。

---

#### `bot_message_broadcast` — 消息广播记录

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `sender_id` | BIGINT | 发送者 | 谁发的广播 |
| `title` | VARCHAR(128) | 广播标题 | |
| `content` | TEXT | 广播内容 | 支持 Markdown |
| `target_type` | TINYINT | 1=全员 2=指定群 3=指定部门 4=指定用户 | |
| `target_ids` | JSON | 目标 ID 列表 | 如果是"指定群"，这里存群 ID 数组 |
| `status` | TINYINT | 1=待发送 2=发送中 3=已完成 4=部分失败 | 异步发送需要跟踪状态 |
| `success_count` | INT | 成功送达数 | |
| `fail_count` | INT | 失败数 | |
| `create_time` | DATETIME | 创建时间 | |
| `complete_time` | DATETIME | 完成时间 | |

---

### 3.3 企业效率（3 张表）

#### `bot_schedule` — 日程表

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `user_id` | BIGINT | 创建者 | 谁的日程 |
| `title` | VARCHAR(128) | 日程标题 | |
| `description` | TEXT | 日程详情 | 可选，用户可能只写了"开会"没有细节 |
| `schedule_time` | DATETIME | 日程时间 | `/schedule 明天下午3点 开会` → 解析后存这里 |
| `end_time` | DATETIME | 结束时间 | 如果用户写了"3点到4点开会" |
| `remind_before_min` | INT | 提前多少分钟提醒 | 默认 15 分钟 |
| `feishu_calendar_id` | VARCHAR(64) | 飞书日历事件 ID | 调飞书日历 API 创建后，飞书会返回一个 ID，存下来方便后续取消或修改 |
| `reminded` | TINYINT | 0=未提醒 1=已提醒 | 定时任务扫这个字段，提醒过了就不再提醒 |
| `create_time` | DATETIME | 创建时间 | |

---

#### `bot_meeting` — 会议预约表

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `creator_id` | BIGINT | 创建者 | |
| `title` | VARCHAR(128) | 会议主题 | |
| `meeting_time` | DATETIME | 会议时间 | |
| `duration_min` | INT | 时长(分钟) | |
| `attendee_ids` | JSON | 参会人员 ID 列表 | |
| `room` | VARCHAR(64) | 会议室 | |
| `feishu_meeting_id` | VARCHAR(64) | 飞书会议 ID | 调飞书 API 创建后返回的 |
| `status` | TINYINT | 1=待召开 2=已召开 3=已取消 | |
| `create_time` | DATETIME | 创建时间 | |

---

#### `bot_approval_reminder` — 审批提醒表

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `approval_id` | VARCHAR(64) | 飞书审批实例 ID | 飞书审批的唯一标识 |
| `approver_id` | BIGINT | 审批人 | 谁需要审批 |
| `applicant_name` | VARCHAR(64) | 申请人姓名 | 提示消息用："张三的请假申请等待你审批" |
| `title` | VARCHAR(128) | 审批标题 | |
| `status` | TINYINT | 1=待审批 2=已通过 3=已拒绝 | |
| `last_remind_time` | DATETIME | 上次提醒时间 | 避免频繁骚扰，比如每小时最多提醒一次 |
| `remind_count` | INT | 已提醒次数 | 超过一定次数（如 5 次）就不再提醒 |
| `create_time` | DATETIME | 创建时间 | |

---

### 3.4 知识库（1 张表）

#### `bot_knowledge_doc` — 知识库文档

**这就是你问的"知识库的文档信息是不是要存起来"——对。**

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `title` | VARCHAR(255) | 文档标题 | 搜索匹配用 |
| `content` | LONGTEXT | 文档正文 | 全文搜索的关键内容 |
| `summary` | VARCHAR(512) | AI 生成的摘要 | 搜索时先展示摘要，用户点进去再看全文 |
| `category` | VARCHAR(64) | 分类 | 如"技术文档"、"产品需求"、"会议纪要" |
| `source_type` | TINYINT | 来源 | 1=飞书云文档 2=手动录入 3=网页抓取 |
| `source_url` | VARCHAR(512) | 原文链接 | 如果是飞书文档就有链接 |
| `tags` | JSON | 标签 | `["Java","部署","CI/CD"]`，辅助搜索 |
| `view_count` | INT | 浏览次数 | 统计哪些文档最常用 |
| `status` | TINYINT | 1=已发布 0=已下架 | |
| `create_by` | BIGINT | 录入者 | |
| `create_time` | DATETIME | 创建时间 | |
| `update_time` | DATETIME | 更新时间 | 文档可能会被编辑 |

**设计思考**：`content` 用 LONGTEXT 而不是 VARCHAR，因为一篇文档可能上万字。另外你需要建**全文索引**（FULLTEXT INDEX）在 `title` 和 `content` 字段上，否则 `/search` 搜索会很慢。

---

### 3.5 Git 与 DevOps（5 张表）

#### `bot_git_repo` — Git 仓库配置

**这就是你问的"仓库配置要不要存"——对。**

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `alias` | VARCHAR(32) | 仓库别名 | 用户打 `/gitlog backend` 时，"backend"就是别名。**唯一索引** |
| `repo_url` | VARCHAR(512) | 仓库地址 | 如 `https://gitlab.com/team/backend.git` |
| `platform` | TINYINT | 平台 | 1=GitLab 2=GitHub 3=Gitee，不同平台的 API 不一样 |
| `access_token` | VARCHAR(255) | 访问令牌 | 加密存储！用 AES 加密，不能明文存 |
| `default_branch` | VARCHAR(64) | 默认分支 | 如 `main` 或 `master` |
| `webhook_secret` | VARCHAR(128) | Webhook 密钥 | 验证 webhook 请求的合法性 |
| `code_review_enabled` | TINYINT | 0=否 1=是 | 是否开启自动代码审查 |
| `review_target_chat` | VARCHAR(64) | 审查结果发送到哪个群 | |
| `status` | TINYINT | 1=正常 0=已禁用 | |
| `create_time` | DATETIME | 创建时间 | |

**设计思考**：`access_token` 一定要加密存。这个 Token 能操作你的代码仓库，如果数据库泄露而 Token 明文存储，代码就没了。用 AES 对称加密，密钥放在环境变量里，不在代码里硬编码。

---

#### `bot_deploy_task` — 部署任务

**这就是你问的"部署订单要不要存"——对。**

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `user_id` | BIGINT | 谁触发的 | |
| `repo_id` | BIGINT | 部署哪个仓库 | |
| `environment` | VARCHAR(16) | 环境 | `dev`/`test`/`staging`/`prod` |
| `branch` | VARCHAR(64) | 分支 | |
| `jenkins_job_name` | VARCHAR(128) | Jenkins Job 名 | 触发哪个构建任务 |
| `jenkins_build_id` | VARCHAR(32) | Jenkins 返回的构建 ID | 用来轮询构建状态 |
| `status` | TINYINT | 状态 | 1=排队中 2=构建中 3=成功 4=失败 5=已取消 |
| `build_log_url` | VARCHAR(512) | 构建日志链接 | 失败了用户可以点进去看日志 |
| `confirmed` | TINYINT | 0=未确认 1=已确认 | **生产环境部署需要二次确认**，防止误操作 |
| `start_time` | DATETIME | 开始时间 | |
| `end_time` | DATETIME | 结束时间 | |
| `create_time` | DATETIME | 创建时间 | |

**设计思考**：`status` 有 5 个状态而不是简单的"成功/失败"。因为部署是异步的（Jenkins 构建要几分钟），用户发 `/deploy` 后不会一直等待，机器人先回"部署已提交，构建中..."，构建完了再发回调消息通知。这张表全程跟踪一个部署的生命周期。

---

#### `bot_code_review` — 代码审查记录

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `repo_id` | BIGINT | 关联仓库 | |
| `commit_hash` | VARCHAR(40) | 提交哈希 | Git commit 的 SHA |
| `mr_id` | VARCHAR(32) | MR/PR 编号 | 如果是 Merge Request 触发的 |
| `branch` | VARCHAR(64) | 分支名 | |
| `author` | VARCHAR(64) | 提交者 | |
| `diff_content` | LONGTEXT | 变更内容 | 原始 diff，用于追溯 |
| `review_result` | JSON | 审查结果 | 结构化结果：`{"score":85,"issues":[{"severity":"high","file":"UserService.java","line":42,"message":"NPE风险","suggestion":"加空判断"}]}` |
| `ai_model` | VARCHAR(32) | 使用的模型 | 记录用哪个模型审的，方便对比效果 |
| `status` | TINYINT | 1=审查中 2=已完成 3=失败 | |
| `review_time_ms` | INT | 审查耗时(ms) | |
| `create_time` | DATETIME | 创建时间 | |

**设计思考**：`review_result` 用 JSON 类型而不是拆成多张表。因为每次审查的问题数量和结构都不同（有的只查出 2 个问题，有的查出 20 个），关系型建模会导致大量空字段或过度复杂的关联。JSON 列在 MySQL 5.7+ 里支持索引和查询，适合这种"结构不固定的嵌套数据"。

---

#### `bot_jira_config` — JIRA 配置

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `name` | VARCHAR(64) | 配置名 | 如"默认"、"研发项目" |
| `jira_url` | VARCHAR(255) | JIRA 地址 | |
| `username` | VARCHAR(64) | 用户名 | |
| `api_token` | VARCHAR(255) | API Token | 加密存储 |
| `default_project_key` | VARCHAR(32) | 默认项目 Key | 如"DEV"，创建工单时如果用户没指定就用这个 |
| `status` | TINYINT | 1=正常 0=禁用 | |
| `create_time` | DATETIME | 创建时间 | |

---

#### `bot_monitor_config` — 监控配置

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `service_name` | VARCHAR(64) | 服务名 | `/monitor user-service` 里的 "user-service" |
| `display_name` | VARCHAR(64) | 显示名 | 中文名，消息卡片展示用 |
| `prometheus_url` | VARCHAR(255) | Prometheus 地址 | |
| `health_endpoint` | VARCHAR(255) | 健康检查地址 | |
| `grafana_dashboard_url` | VARCHAR(512) | Grafana 面板链接 | 生成消息时附带一个"查看 Grafana"的链接 |
| `status` | TINYINT | 1=正常 0=禁用 | |
| `create_time` | DATETIME | 创建时间 | |

---

### 3.6 扩展框架（1 张表）

#### `bot_plugin_config` — 插件/自定义指令配置

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `command` | VARCHAR(32) | 指令名 | 如 `/ping`、`/dinner` |
| `description` | VARCHAR(128) | 指令说明 | `/help` 时展示 |
| `handler_class` | VARCHAR(255) | 处理器类全限定名 | 如果是 Java 类注册的 |
| `config_json` | JSON | 指令配置参数 | 灵活扩展，不同的指令有不同的配置需求 |
| `required_roles` | JSON | 需要哪些角色才能用 | `["ADMIN","SUPER_ADMIN"]` |
| `cooldown_seconds` | INT | 冷却时间(秒) | 防止用户疯狂刷指令 |
| `enabled` | TINYINT | 0=禁用 1=启用 | 可以不下线就禁用某个指令 |
| `create_time` | DATETIME | 创建时间 | |
| `update_time` | DATETIME | 更新时间 | |

---

### 3.7 系统表（1 张表）

#### `bot_system_config` — 系统配置（KV 键值表）

| 字段 | 类型 | 说明 | 为什么需要 |
|------|------|------|-----------|
| `id` | BIGINT | 主键自增 | |
| `config_key` | VARCHAR(64) | 配置键 | **唯一索引** |
| `config_value` | TEXT | 配置值 | |
| `description` | VARCHAR(255) | 说明 | 给运维看的 |
| `update_time` | DATETIME | 更新时间 | |

不用建很多张配置表，一张 KV 表就够了。示例数据：
```
config_key: "weather.api_key"      config_value: "sk-xxx"
config_key: "translate.api_key"    config_value: "ak-xxx"
config_key: "ai.default_model"     config_value: "deepseek-v4"
config_key: "bot.max_dialog_turns" config_value: "10"
```

**设计思考**：为什么不给每个配置单独建表（天气配置表、翻译配置表...）？因为系统配置是"零散的小数据"，单独建表会建出几十张两三个字段的表，维护成本极高。一张 KV 表覆盖所有简单配置，代码里 `configService.get("weather.api_key")` 一行搞定。

---

## 四、总表清单

| 序号 | 表名 | 模块归属 | 一句话说明 |
|------|------|---------|-----------|
| 1 | `bot_user` | 权限 | 用户信息，关联飞书身份 |
| 2 | `bot_role` | 权限 | 角色定义 |
| 3 | `bot_user_role` | 权限 | 用户-角色多对多关联 |
| 4 | `bot_command_log` | 核心 | 指令执行日志 |
| 5 | `bot_message_broadcast` | 企业效率 | 消息广播记录 |
| 6 | `bot_schedule` | 企业效率 | 个人日程 |
| 7 | `bot_meeting` | 企业效率 | 会议预约 |
| 8 | `bot_approval_reminder` | 企业效率 | 审批提醒 |
| 9 | `bot_knowledge_doc` | 知识库 | 知识库文档 |
| 10 | `bot_git_repo` | Git/DevOps | 仓库配置 |
| 11 | `bot_deploy_task` | Git/DevOps | 部署任务 |
| 12 | `bot_code_review` | Git/DevOps | 代码审查记录 |
| 13 | `bot_jira_config` | Git/DevOps | JIRA 配置 |
| 14 | `bot_monitor_config` | Git/DevOps | 监控服务配置 |
| 15 | `bot_plugin_config` | 插件框架 | 自定义指令配置 |
| 16 | `bot_system_config` | 系统 | KV 系统配置 |

---

## 五、三张高频查询表的索引建议

### `bot_command_log`
```sql
INDEX idx_user_time (user_id, create_time)   -- 查某个用户的历史指令
INDEX idx_command_time (command, create_time) -- 统计某指令的使用趋势
INDEX idx_chat_time (chat_id, create_time)    -- 查某个群的指令记录
```

### `bot_knowledge_doc`
```sql
FULLTEXT INDEX ft_title_content (title, content)  -- /search 全文搜索
INDEX idx_category (category)                      -- 按分类筛选
INDEX idx_status (status)                          -- 只查已发布的
```

### `bot_deploy_task`
```sql
INDEX idx_status (status)                          -- 轮询还在执行中的任务
INDEX idx_user_time (user_id, create_time)         -- 某人最近的部署记录
```

---

## 六、给新手的设计自查清单

每次设计完表后，问自己这 5 个问题：

1. **每个表的主键是什么？** 必须有主键，整型自增是最安全的选择
2. **哪些字段应该建索引？** WHERE 条件里的字段、JOIN 关联的字段、排序的字段
3. **字段有没有合适的默认值？** 状态字段默认 1，时间字段默认 NOW()
4. **密码/Token 加密了吗？** 明文存就是定时炸弹
5. **表名/字段名一致吗？** 全用 `create_time` 不要混用 `created_at`、`createTime`

---

## 七、不需要建表的场景（别过度设计）

有些数据**不适合存 MySQL**，别硬塞：

| 数据类型 | 存哪里 | 原因 |
|----------|--------|------|
| 多轮对话上下文 | **Redis** | 临时数据，对话结束就过期，用 TTL 自动清理 |
| Webhook 幂等 Key | **Redis** | 飞书可能重复推送同一条消息，用 Redis 的 SETNX 去重 |
| 接口调用限流计数 | **Redis** | 高频写入（每次请求都要 ++），MySQL 扛不住 |
| 天气数据 | **不存** | 实时查询外部 API，缓存到 Redis 几分钟就行 |
| 翻译结果 | **不存** | 除非要做翻译记录回溯，否则没必要 |

**判断标准**：这个数据是"业务资产"还是"临时状态"？资产存 MySQL，临时状态放 Redis。
