演示视频的补充:
通过网盘分享的文件：屏幕录制 2026-06-04 181053.mp4 链接: https://pan.baidu.com/s/1801FRyX9WU71dFOKNUgN-Q?pwd=1111 提取码: 1111

飞书群聊:
通过网盘分享的文件：585cc67569fde2ada62bbf0d0650b04c.jpg
链接: https://pan.baidu.com/s/1Kp-VBwC0LvBGY6DiWEkKoQ?pwd=1111 提取码: 1111

演示视频:
通过网盘分享的文件：屏幕录制 2026-05-31 150507.mp4 链接: https://pan.baidu.com/s/1-xS0u54ek0G8eCltjo0_1g?pwd=1111 提取码: 1111


# 飞书超级助手 — 企业效率机器人

基于 Spring Boot 3 的飞书群机器人，集成天气查询、翻译、日程创建、知识库 AI 问答、代码审查、消息广播等功能，通过 7 模块 Maven 项目 + 插件化指令框架实现。


## 指令列表

| 指令 | 功能 | 权限 |
|------|------|------|
| `/help` | 显示所有可用指令 | 所有人 |
| `/weather <城市>` | 查询实时天气 | 所有人 |
| `/translate <文本> [to 语言]` | 多语种翻译 | 所有人 |
| `/group <群名> [成员...]` | 创建群聊，支持按姓名或 open_id 拉人 | 所有人 |
| `/schedule <时间> <事件>` | 创建日程并同步飞书日历 | 所有人（日历同步需 OAuth 授权） |
| `/search <关键词>` | 搜索知识库文档 | 所有人 |
| `/search-ai <问题>` | AI 阅读理解知识库并回答 | 所有人 |
| `/gitlog <仓库路径>` | 查看仓库最近提交 | 所有人 |
| `/gitdiff <仓库路径> <sha>` | 查看提交代码差异 | 所有人 |
| `/review <代码片段>` | AI 代码审查 | 所有人 |
| `/mergestatus <仓库路径> <PR编号>` | 查看 Pull Request 状态 | 所有人 |
| `/broadcast [群名] \| 标题 \| 内容` | 消息广播 | ADMIN 及以上 |

### 系统功能

- **多轮对话**：Redis 上下文记忆 + AI 意图解析，首次输入指令后无需重复 `/` 前缀
- **自动代码审查**：Gitee push Webhook 触发 AI 审查，结果自动推送飞书群
- **审批催办**：飞书审批事件监听 + 定时任务自动催办，私聊/群聊三层兜底发送
- **PR 状态查询**：`/mergestatus` 查询 Gitee PR 的合并状态和冲突情况
- **用户体系**：首次发消息自动注册 + 4 级角色权限（SUPER_ADMIN / ADMIN / USER / READONLY）
- **群名映射**：自动采集群名存 Redis，广播时用群名替代 `oc_xxx` 不可读 ID

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.6, Maven 多模块 |
| 数据库 | MySQL 5.7+, MyBatis-Plus 3.5.9 |
| 缓存 | Redis 7, Redisson |
| AI | DeepSeek（OpenAI 兼容接口） |
| 工具 | Hutool, Lombok, WebClient |
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
git clone https://gitee.com/haohao73/feishu_bot.git
git clone https://github.com/haohao73/feishu-super-bot.git
(gitee和github上均上传)
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

## Webhook 配置教程

本项目使用两个 Webhook 端点接收外部推送：

### 飞书 Webhook（消息 + 审批事件）

**端点**：`POST /webhook/event`

**配置位置**：飞书开放平台 → 事件订阅 → 请求网址

```
URL: http://feishubot.nat300.top/webhook/event  （或你的 natapp 域名）
```

**需订阅的事件**：

| 事件名 | 用途 |
|--------|------|
| `im.message.receive_v1` | 接收用户@机器人的消息 |
| `approval_instance` | 审批实例状态变更（提交/通过/拒绝） |
| `leave_approval` | 请假审批事件 |

**需要开通的权限**：

| 权限 | 用途 |
|------|------|
| `im:message` | 发送消息 |
| `im:chat` | 创建群聊、添加成员 |
| `contact:contact.base:readonly` | 按姓名查 open_id（/group 拉人用） |
| `calendar:calendar:write` | 同步日程到飞书日历 |
| `approval:approval` | 访问审批应用 |

### Gitee Webhook（Push 自动代码审查）

**端点**：`POST /webhook/gitee`

**配置位置**：Gitee 仓库 → 管理 → Webhooks → 添加

| 配置项 | 值 |
|--------|---|
| URL | `http://feishubot.nat300.top/webhook/gitee` |
| 事件 | Push |
| 密码 | 留空（当前未开验签） |

配置后每次 `git push`，Gitee 推送事件 → bot 自动获取 diff → AI 审查 → 结果发飞书群。

### 调试 Webhook

1. **curl 本地测试**：`curl -X POST http://localhost:8080/webhook/event -H "Content-Type: application/json" -d '{"type":"event_callback",...}'`
2. **检查 natapp**：浏览器打开 `http://你的natapp域名/webhook/event`，有响应说明隧道通
3. **飞书测试按钮**：飞书开放平台 → 事件订阅 → 点"测试"，看控制台有没有 `POST body:` 日志

---

## 添加自定义指令

本项目使用**插件化指令框架**。新增一个指令只需 3 步，不改任何已有代码。

### 步骤 1：新建 Handler 类

在 `feishu-bot-core/src/main/java/.../handler/` 下新建类：

```java
package com.bluemountain.bot.core.handler;

import com.bluemountain.bot.common.dto.CommandContext;
import com.bluemountain.bot.plugin.CommandPlugin;
import org.springframework.stereotype.Component;

@Component  // ← Spring 自动发现
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
        String args = ctx.getArgs();       // 拿参数
        // ... 你的业务逻辑 ...
        return "回复内容（支持飞书 Markdown）";
    }
}
```

### 步骤 2（可选）：需要调第三方 API 时

在 `feishu-bot-integration` 模块建 Client 类：

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

然后在 Handler 里注入 `MyClient`，调它的方法。

### 步骤 3（可选）：需要数据库时

在 `feishu-bot-infrastructure` 模块建 Entity + Mapper：

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

在 `sql/init.sql` 里加建表语句。

### 关键约定

| 约定 | 说明 |
|------|------|
| Handler 放 core 模块 | 实现 `CommandPlugin`，标 `@Component` |
| Client 放 integration 模块 | 调第三方 API，标 `@Component` |
| Entity/Mapper 放 infrastructure 模块 | 数据库操作，继承 `BaseMapper` |
| 密钥放 `application-local.yml` | 不提交 Git |
| API Host 放 `application.yml` | 可以提交 Git |

写完 Handler 后重启项目，`/help` 自动展示新指令，`CommandRouter` 自动路由到它。**不改任何已有代码——这就是插件化框架的核心价值。**

---
