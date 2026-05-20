-- 插入知识库文档
USE feishu_bot;

-- 文档1：spec.md
INSERT INTO bot_knowledge_doc (title, content, summary, category, tags, status) VALUES (
'飞书超级助手机器人 — 项目总体设计方案',
'飞书超级助手机器人是面向企业内部使用的飞书效率机器人。技术栈：Spring Boot 3 单体架构 + MySQL + Redis + RabbitMQ。核心模块包括：基础指令集（/weather、/schedule、/group、/search、/translate、/help）、企业效率集成（消息广播、审批提醒、会议预约）、Git 集成（/gitlog、/gitdiff、/createbranch）、自动代码审查（Webhook + AI）、CI/CD 触发、JIRA 工单查询。架构采用 Maven 多模块，共 7 个子模块：common、api、core、integration、auth、plugin、infrastructure。部署使用 Docker Compose 一键启动。',
'飞书机器人完整技术方案，包含架构设计、技术选型、模块划分、部署方案。关键词：Spring Boot 3、Maven多模块、Docker Compose',
'技术文档',
'["架构设计","技术选型","Spring Boot","飞书","Docker"]',
1
);

-- 文档2：database-design.md
INSERT INTO bot_knowledge_doc (title, content, summary, category, tags, status) VALUES (
'飞书超级助手机器人 — 数据库设计文档',
'数据库采用 MySQL，共 16 张表。用户权限模块：bot_user（用户表）、bot_role（角色表）、bot_user_role（用户角色关联表）。指令日志：bot_command_log（指令执行日志）。企业效率：bot_schedule（日程表）、bot_meeting（会议表）、bot_approval_reminder（审批提醒表）。知识库：bot_knowledge_doc（知识库文档表）。Git与DevOps：bot_git_repo（仓库配置）、bot_deploy_task（部署任务）、bot_code_review（代码审查记录）、bot_jira_config（JIRA配置）、bot_monitor_config（监控配置）。扩展框架：bot_plugin_config（插件配置）。系统配置：bot_system_config（系统KV配置表）。设计原则：表名前缀隔离（bot_）、JSON字段存非结构化数据、密码和Token加密存储、FULLTEXT索引用于搜索。',
'16张表的完整数据库设计，含索引策略和设计思路。关键词：MySQL、MyBatis-Plus、表设计、索引、全文搜索',
'技术文档',
'["数据库","MySQL","表设计","索引","MyBatis-Plus"]',
1
);

-- 验证
SELECT id, title, CHAR_LENGTH(content) AS 内容长度 FROM bot_knowledge_doc;
