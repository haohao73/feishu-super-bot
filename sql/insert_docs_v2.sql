USE feishu_bot;

INSERT INTO bot_knowledge_doc (title, content, summary, category, tags, status) VALUES
('飞书超级助手机器人 — 项目总体设计方案', '飞书超级助手机器人是面向企业内部使用的飞书效率机器人。技术栈 Spring Boot 3 单体架构加 MySQL 加 Redis 加 RabbitMQ。核心模块包括：基础指令集（weather schedule group search translate help）、企业效率集成（消息广播 审批提醒 会议预约）、Git 集成（gitlog gitdiff createbranch）、自动代码审查（Webhook 加 AI）、CI CD 触发、JIRA 工单查询。架构采用 Maven 多模块，共 7 个子模块：common api core integration auth plugin infrastructure。部署使用 Docker Compose 一键启动。', '飞书机器人完整技术方案', '技术文档', '["架构设计","Spring Boot","Docker"]', 1),

('飞书超级助手机器人 — 数据库设计文档', '数据库采用 MySQL 共 16 张表。用户权限模块：bot_user bot_role bot_user_role。指令日志：bot_command_log。企业效率：bot_schedule bot_meeting bot_approval_reminder。知识库：bot_knowledge_doc。Git与DevOps：bot_git_repo bot_deploy_task bot_code_review bot_jira_config bot_monitor_config。设计原则：表名前缀隔离 JSON字段存非结构化数据 密码Token加密存储 FULLTEXT索引用搜索。', '16张表的完整数据库设计', '技术文档', '["数据库","MySQL","表设计"]', 1);
