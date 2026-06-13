# Gitee 集成与 Git 操作详解 — 飞书超级助手

> 覆盖 4 个 Git 相关功能：Webhook 自动代码审查、/gitlog 提交日志、/gitdiff 差异对比、/mergestatus 合并请求。

---

## 目录

- [第一章：整体架构——四个功能一张网](#第一章整体架构四个功能一张网)
- [第二章：Gitee Webhook — Push 自动触发代码审查](#第二章gitee-webhook--push-自动触发代码审查)
- [第三章：/gitlog — 查看提交日志](#第三章gitlog--查看提交日志)
- [第四章：/gitdiff — 查看提交差异](#第四章gitdiff--查看提交差异)
- [第五章：/mergestatus — 查看 PR 状态](#第五章mergestatus--查看-pr-状态)
- [第六章：GiteeClient 统一设计](#第六章giteeclient-统一设计)

---

## 第一章：整体架构——四个功能一张网

### 1.1 四个功能的关系

```
                     Gitee API
                        │
        ┌───────────────┼───────────────┐
        │               │               │
   主动查询(飞书指令)   被动接收(Webhook)  主动查询(飞书指令)
        │               │               │
   /gitlog           Push事件         /mergestatus
   /gitdiff              │
                   自动代码审查
                   推送到飞书群
```

- **主动查询**：用户在飞书群敲指令 → 机器人调 Gitee API → 返回结果
- **被动接收**：用户 push 代码 → Gitee 推 Webhook 到机器人 → 自动审查并通知

### 1.2 涉及文件

| 模块 | 文件 | 作用 |
|------|------|------|
| api | `GiteeWebhookController.java` | 接收 Gitee push Webhook |
| core | `GitReviewService.java` | 解析 push 事件 → 调 AI 审查 → 发飞书群 |
| core | `GitLogHandler.java` | `/gitlog` 指令 |
| core | `GitDiffHandler.java` | `/gitdiff` 指令 |
| core | `MergeStatusHandler.java` | `/mergestatus` 指令 |
| integration | `GiteeClient.java` | 所有 Gitee API 调用（5 个方法） |

---

## 第二章：Gitee Webhook — Push 自动触发代码审查

### 2.1 工作流程

```
开发者 push 代码到 Gitee
      │
      ▼
Gitee 发送 POST https://feishubot.nat300.top/webhook/gitee
      │
      ▼
GiteeWebhookController 接收
      │
      ▼ 异步丢线程池
GitReviewService.handlePushEvent(body)
      │
      ├─ ① 解析 push 事件 JSON，提取 commits
      ├─ ② 调 Gitee API 获取代码 diff
      ├─ ③ 拼审查 Prompt
      ├─ ④ 调 DeepSeek AI 审查
      └─ ⑤ 审查结果发送到飞书群
```

### 2.2 接收端点 — GiteeWebhookController

```java
@PostMapping("/webhook/gitee")
public Object onPush(HttpServletRequest request) {
    String body = readBody(request);                        // ① 读请求体
    executor.submit(() -> gitReviewService.handlePushEvent(body)); // ② 异步处理
    return Map.of("ok", true);                             // ③ 立刻返回 200
}
```

**和飞书 Webhook 完全一样的套路**：读 body → 异步处理 → 立即返回 200。

### 2.3 核心处理 — GitReviewService

```java
public void handlePushEvent(String body) {
    JSONObject root = JSONUtil.parseObj(body);

    // ① 解析 push 事件
    String repoPath = root.getJSONObject("repository").getStr("path");
    String before = root.getStr("before");    // push 前的最新 commit
    String after = root.getStr("after");      // push 后的最新 commit
    JSONArray commits = root.getJSONArray("commits");

    // ② 获取实际代码 diff（Webhook 不含 diff，需调 Compare API）
    String diff = giteeClient.getCompareDiff(repoPath, before, after);

    // ③ 构造 AI 审查 prompt
    for (int i = 0; i < commits.size(); i++) {
        JSONObject commit = commits.getJSONObject(i);
        String author = commit.getJSONObject("author").getStr("name");
        String message = commit.getStr("message");

        String prompt = """
            你是资深代码审查专家。审查以下代码变更：

            **提交信息**：%s
            **作者**：%s
            **仓库**：%s

            **代码变更**：
            %s

            请指出：1. 潜在 bug   2. 安全风险   3. 改进建议
            """.formatted(message, author, repoPath, diff);

        String review = aiClient.chat(SYSTEM_PROMPT, prompt);

        // ④ 发送审查结果到飞书群
        feishuClient.sendTextMessage(reviewChatId, review);
    }
}
```

**关键设计**：Gitee push Webhook 只通知"谁 push 了什么 commit"，不含实际代码差异。所以收到 Webhook 后，还需要**再调一次 Gitee Compare API** 来拿 diff。

### 2.4 拿 diff — GiteeClient.getCompareDiff

```java
public String getCompareDiff(String repoPath, String before, String after) {
    String url = "https://gitee.com/api/v5/repos/" + repoPath
               + "/compare/" + before + "..." + after;

    Map<String, Object> resp = webClient.get()
            .uri(url)
            .header("Authorization", "Bearer " + giteeToken)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

    // Gitee 返回 files 列表，每个 file 含 filename + patch
    StringBuilder diff = new StringBuilder();
    for (Map<String, Object> file : (List<Map<String, Object>>) resp.get("files")) {
        diff.append("文件：").append(file.get("filename")).append("\n");
        diff.append(file.get("patch")).append("\n\n");
    }
    return diff.toString();
}
```

### 2.5 Gitee 端的配置

在 Gitee 仓库 → 管理 → Webhooks → 添加：

| 配置项 | 值 |
|--------|---|
| URL | `http://feishubot.nat300.top/webhook/gitee` |
| 事件 | Push |
| 密码 | 留空（当前未开验签） |

---

## 第三章：/gitlog — 查看提交日志

### 3.1 使用示例

```
/gitlog haohao73/feishu_bot
```

### 3.2 返回效果

```
最近提交 — haohao73/feishu_bot

1. 100c8de 修改 — haohao73
2. a1d18d7 RestTemplate → WebClient 迁移 — haohao73
3. d957646 合并远程分支 — haohao73
4. 41b8c85 修改 — haohao73
5. 05fcc6e 补传 .gitignore — haohao73

输入 /gitdiff <sha> 查看某次提交的差异
```

### 3.3 实现

```java
@Component
public class GitLogHandler implements CommandPlugin {

    private final GiteeClient giteeClient;

    @Override
    public String name() { return "gitlog"; }

    @Override
    public String execute(CommandContext ctx) {
        String repoPath = ctx.getArgs().trim();

        // 调 Gitee API：GET /repos/{repo}/commits?per_page=5
        List<Map<String, Object>> commits = giteeClient.getCommits(repoPath);

        // 逐条拼 Markdown
        for (Map<String, Object> c : commits) {
            String sha = ((String) c.get("sha")).substring(0, 7);
            Map<String, Object> commit = (Map<String, Object>) c.get("commit");
            String message = (String) commit.get("message");
            Map<String, Object> author = (Map<String, Object>) commit.get("author");
            sb.append("- `").append(sha).append("` ").append(message)
              .append(" — ").append(author.get("name")).append("\n");
        }
        return sb.toString();
    }
}
```

**Gitee API**：`GET /api/v5/repos/{owner}/{repo}/commits?per_page=5`

返回一个数组，每个元素包含 `sha`、`commit.message`、`commit.author.name`。

---

## 第四章：/gitdiff — 查看提交差异

### 4.1 使用示例

```
/gitdiff haohao73/feishu_bot 100c8de
```

### 4.2 返回效果

```
代码差异 — haohao73/feishu_bot @ 100c8de

文件：README.md
@@ -41,7 +41,7 @@
-| 工具 | Hutool, Lombok, RestTemplate |
+| 工具 | Hutool, Lombok, WebClient |

文件：feishu-bot-integration/.../FeishuClient.java
@@ -1,480 +1,300 @@
-    private final RestTemplate restTemplate;
+    private final WebClient webClient;
...
```

### 4.3 实现

```java
@Component
public class GitDiffHandler implements CommandPlugin {

    private final GiteeClient giteeClient;

    @Override
    public String name() { return "gitdiff"; }

    @Override
    public String execute(CommandContext ctx) {
        String[] parts = ctx.getArgs().trim().split("\\s+");
        String repoPath = parts[0];
        String sha = parts[1];

        String diff = giteeClient.getCommitDiff(repoPath, sha);

        return "**代码差异** — `" + repoPath + "` @ `" + sha.substring(0, 7) + "`\n\n" + diff;
    }
}
```

**Gitee API**：`GET /api/v5/repos/{owner}/{repo}/commits/{sha}`

返回该 commit 的详细信息，包含 `files` 数组，每个文件有 `filename` 和 `patch` 字段。

---

## 第五章：/mergestatus — 查看 PR 状态

### 5.1 使用示例

```
/mergestatus haohao73/feishu_bot 1
```

### 5.2 返回效果

```
PR #1 — haohao73/feishu_bot

标题：测试PR - 用于演示/mergestatus指令
状态：🟡 待合并
合并状态：✅ 无冲突，可合并
作者：haohao73
分支：test-pr-branch
创建时间：2026-06-03T08:30:00+08:00

查看详情
```

### 5.3 实现

```java
@Component
public class MergeStatusHandler implements CommandPlugin {

    @Override
    public String execute(CommandContext ctx) {
        String[] parts = ctx.getArgs().trim().split("\\s+");
        String repoPath = parts[0];
        int prNumber = Integer.parseInt(parts[1]);

        Map<String, Object> pr = giteeClient.getPullRequest(repoPath, prNumber);

        String state = (String) pr.getOrDefault("state", "unknown");
        String stateText = switch (state) {
            case "open" -> "🟡 待合并";
            case "merged" -> "🟢 已合并";
            case "closed" -> "🔴 已关闭";
            default -> "❓ " + state;
        };

        return String.format("PR #%d — %s\n标题：%s\n状态：%s\n...",
                prNumber, repoPath, pr.get("title"), stateText);
    }
}
```

**Gitee API**：`GET /api/v5/repos/{owner}/{repo}/pulls/{number}`

返回 PR 详情，关键字段：`state`（open/merged/closed）、`mergeable`（是否有冲突）、`user.login`（作者）、`html_url`（网页链接）。

---

## 第六章：GiteeClient 统一设计

### 6.1 五个方法一览

| 方法 | HTTP | Gitee API | 用途 |
|------|------|-----------|------|
| `getCommits()` | GET | `/commits?per_page=5` | /gitlog |
| `getCommitDiff()` | GET | `/commits/{sha}` | /gitdiff |
| `getCompareDiff()` | GET | `/compare/{a}...{b}` | Webhook 审查 |
| `getPullRequest()` | GET | `/pulls/{number}` | /mergestatus |

### 6.2 统一的设计模式

所有方法共享同一套代码骨架：

```java
@Slf4j
@Component
public class GiteeClient {

    private final WebClient webClient;       // ① 统一的 HTTP 客户端
    private String giteeToken;               // ② 从配置读取

    // ③ 统一的鉴权请求构造
    private WebClient.RequestHeadersSpec<?> getWithAuth(String url) {
        var spec = webClient.get().uri(url);
        if (giteeToken != null && !giteeToken.isBlank()) {
            spec.header("Authorization", "Bearer " + giteeToken);
        }
        return spec;
    }

    // ④ 每个方法都是：拼 URL → getWithAuth → block → 解析
    public XxxResponse someMethod(String repoPath, ...) {
        String url = GITEE_API + repoPath + "/some-endpoint";
        try {
            Map<String, Object> resp = getWithAuth(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            // 解析 resp → 返回
        } catch (Exception e) {
            log.warn("调用失败 | msg={}", e.getMessage());
            return null;  // 异常隔离
        }
    }
}
```

### 6.3 可选鉴权

```java
if (giteeToken != null && !giteeToken.isBlank()) {
    spec.header("Authorization", "Bearer " + giteeToken);
}
```

公开仓库不需要 Token（但 API 频率限制更低）。私有仓库必须配 Token。Token 在 `application-local.yml` 里：

```yaml
gitee:
  token: eef4310c7306a2bce620fe31e06eecfa
```

### 6.4 为什么 Gitee 选 Gitee 不选 GitHub

- 国内直连，不需要代理
- API 格式和 GitHub 90% 相似（都是 RESTful，都返回 JSON）
- 免费私有仓库
- 和飞书一样，natapp 隧道直达

**如果以后要切 GitHub，只需要改 `GITEE_API` 常量和 Header 格式——Handler 一行不用动。**

---

## 总结

四个 Git 功能本质上都是**把 Gitee 的 REST API 包了一层飞书指令**：

1. **Webhook 自动审查**：被动接收 push 事件 → 拿 diff → AI 审查 → 推飞书群
2. **/gitlog**：调 commits API → 拼 Markdown 列表
3. **/gitdiff**：调 commit API → 展示文件差异
4. **/mergestatus**：调 PR API → 展示合并状态

每个功能 = 1 个 Handler + GiteeClient 里的 1 个方法。Handler 只管拼回复格式，Client 只管调 API。层级分明，互不干扰。
