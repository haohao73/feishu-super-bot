-- ============================================================
-- 飞书超级助手机器人 — 数据库初始化脚本
-- MySQL 5.7+
-- ============================================================

CREATE DATABASE IF NOT EXISTS feishu_bot
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE feishu_bot;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feishu_open_id VARCHAR(64) NOT NULL COMMENT '飞书用户唯一标识',
    feishu_union_id VARCHAR(64) DEFAULT '' COMMENT '飞书统一ID',
    name VARCHAR(64) DEFAULT '' COMMENT '用户姓名',
    avatar_url VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    department VARCHAR(128) DEFAULT '' COMMENT '部门名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_open_id (feishu_open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL COMMENT '角色编码 SUPER_ADMIN/ADMIN/USER/READONLY',
    role_name VARCHAR(32) NOT NULL COMMENT '角色显示名',
    description VARCHAR(255) DEFAULT '' COMMENT '角色描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- 3. 用户角色关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 4. 指令执行日志
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_command_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT '' COMMENT '执行者open_id',
    command VARCHAR(64) NOT NULL COMMENT '指令名',
    raw_message TEXT COMMENT '原始消息全文',
    params JSON COMMENT '解析后的参数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=成功 2=失败 3=权限拒绝 4=执行中',
    error_msg VARCHAR(1024) DEFAULT '' COMMENT '错误信息',
    chat_id VARCHAR(64) DEFAULT '' COMMENT '飞书群ID',
    execute_time_ms INT DEFAULT 0 COMMENT '执行耗时(毫秒)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_command_time (command, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指令执行日志';

-- ============================================================
-- 5. 消息广播记录
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_message_broadcast (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    title VARCHAR(128) DEFAULT '' COMMENT '广播标题',
    content TEXT COMMENT '广播内容',
    target_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=全员 2=指定群 3=指定部门 4=指定用户',
    target_ids JSON COMMENT '目标ID列表',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=待发送 2=发送中 3=已完成 4=部分失败',
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息广播记录';

-- ============================================================
-- 6. 日程表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '用户飞书open_id',
    title VARCHAR(128) NOT NULL COMMENT '日程标题',
    description TEXT COMMENT '日程详情',
    schedule_time DATETIME NOT NULL COMMENT '日程时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    remind_before_min INT DEFAULT 15 COMMENT '提前提醒分钟数',
    feishu_calendar_id VARCHAR(64) DEFAULT '' COMMENT '飞书日历事件ID',
    reminded TINYINT NOT NULL DEFAULT 0 COMMENT '0=未提醒 1=已提醒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, schedule_time),
    INDEX idx_reminded (reminded, schedule_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程表';

-- ============================================================
-- 7. 会议预约表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_meeting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    title VARCHAR(128) NOT NULL COMMENT '会议主题',
    meeting_time DATETIME NOT NULL COMMENT '会议时间',
    duration_min INT DEFAULT 30 COMMENT '时长(分钟)',
    attendee_ids JSON COMMENT '参会人员ID列表',
    room VARCHAR(64) DEFAULT '' COMMENT '会议室',
    feishu_meeting_id VARCHAR(64) DEFAULT '' COMMENT '飞书会议ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=待召开 2=已召开 3=已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议预约表';

-- ============================================================
-- 8. 审批提醒表
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_approval_reminder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL COMMENT '飞书审批实例ID',
    approver_id BIGINT NULL COMMENT '审批人ID（允许为空，飞书事件未提供时兜底）',
    approver_open_id VARCHAR(64) NULL COMMENT '审批人飞书open_id（未注册bot_user时的兜底）',
    applicant_name VARCHAR(64) DEFAULT '' COMMENT '申请人姓名',
    title VARCHAR(128) DEFAULT '' COMMENT '审批标题',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=待审批 2=已通过 3=已拒绝',
    last_remind_time DATETIME DEFAULT NULL COMMENT '上次提醒时间',
    remind_count INT DEFAULT 0 COMMENT '已提醒次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_approver_status (approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批提醒表';

-- ============================================================
-- 9. 知识库文档
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_knowledge_doc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    content LONGTEXT COMMENT '文档正文',
    summary VARCHAR(512) DEFAULT '' COMMENT 'AI摘要',
    category VARCHAR(64) DEFAULT '' COMMENT '分类',
    source_type TINYINT NOT NULL DEFAULT 1 COMMENT '1=飞书云文档 2=手动录入 3=网页抓取',
    source_url VARCHAR(512) DEFAULT '' COMMENT '原文链接',
    tags JSON COMMENT '标签',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=已发布 0=已下架',
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FULLTEXT INDEX ft_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- ============================================================
-- 10. Git仓库配置
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_git_repo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alias VARCHAR(32) NOT NULL COMMENT '仓库别名',
    repo_url VARCHAR(512) NOT NULL COMMENT '仓库地址',
    platform TINYINT NOT NULL DEFAULT 1 COMMENT '1=GitLab 2=GitHub 3=Gitee',
    access_token VARCHAR(255) DEFAULT '' COMMENT '访问令牌(加密)',
    default_branch VARCHAR(64) DEFAULT 'main' COMMENT '默认分支',
    webhook_secret VARCHAR(128) DEFAULT '' COMMENT 'Webhook密钥',
    code_review_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '0=否 1=是',
    review_target_chat VARCHAR(64) DEFAULT '' COMMENT '审查结果发送群',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git仓库配置';

-- ============================================================
-- 11. 部署任务
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_deploy_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '触发者ID',
    repo_id BIGINT DEFAULT NULL COMMENT '仓库ID',
    environment VARCHAR(16) NOT NULL DEFAULT 'dev' COMMENT '环境 dev/test/staging/prod',
    branch VARCHAR(64) DEFAULT '' COMMENT '分支',
    jenkins_job_name VARCHAR(128) DEFAULT '' COMMENT 'Jenkins Job名',
    jenkins_build_id VARCHAR(32) DEFAULT '' COMMENT 'Jenkins构建ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=排队中 2=构建中 3=成功 4=失败 5=已取消',
    build_log_url VARCHAR(512) DEFAULT '' COMMENT '构建日志链接',
    confirmed TINYINT NOT NULL DEFAULT 0 COMMENT '0=未确认 1=已确认',
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署任务';

-- ============================================================
-- 12. 代码审查记录
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_code_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    repo_id BIGINT DEFAULT NULL COMMENT '仓库ID',
    commit_hash VARCHAR(40) DEFAULT '' COMMENT '提交哈希',
    mr_id VARCHAR(32) DEFAULT '' COMMENT 'MR/PR编号',
    branch VARCHAR(64) DEFAULT '' COMMENT '分支名',
    author VARCHAR(64) DEFAULT '' COMMENT '提交者',
    diff_content LONGTEXT COMMENT '变更内容',
    review_result JSON COMMENT '审查结果 {score, issues}',
    ai_model VARCHAR(32) DEFAULT '' COMMENT '使用的模型',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=审查中 2=已完成 3=失败',
    review_time_ms INT DEFAULT 0 COMMENT '审查耗时(ms)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_repo_commit (repo_id, commit_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查记录';

-- ============================================================
-- 13. JIRA配置
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_jira_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '配置名',
    jira_url VARCHAR(255) NOT NULL COMMENT 'JIRA地址',
    username VARCHAR(64) DEFAULT '' COMMENT '用户名',
    api_token VARCHAR(255) DEFAULT '' COMMENT 'API Token(加密)',
    default_project_key VARCHAR(32) DEFAULT '' COMMENT '默认项目Key',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JIRA配置';

-- ============================================================
-- 14. 监控服务配置
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_monitor_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL COMMENT '服务名',
    display_name VARCHAR(64) DEFAULT '' COMMENT '显示名',
    prometheus_url VARCHAR(255) DEFAULT '' COMMENT 'Prometheus地址',
    health_endpoint VARCHAR(255) DEFAULT '' COMMENT '健康检查地址',
    grafana_dashboard_url VARCHAR(512) DEFAULT '' COMMENT 'Grafana面板链接',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service (service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控服务配置';

-- ============================================================
-- 15. 插件/自定义指令配置
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_plugin_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command VARCHAR(32) NOT NULL COMMENT '指令名',
    description VARCHAR(128) DEFAULT '' COMMENT '指令说明',
    handler_class VARCHAR(255) DEFAULT '' COMMENT '处理器全限定名',
    config_json JSON COMMENT '指令配置参数',
    required_roles JSON COMMENT '需要哪些角色',
    cooldown_seconds INT DEFAULT 3 COMMENT '冷却时间(秒)',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_command (command)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件配置';

-- ============================================================
-- 16. 系统配置（KV键值表）
-- ============================================================
CREATE TABLE IF NOT EXISTS bot_system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(255) DEFAULT '' COMMENT '说明',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- ============================================================
-- 初始数据
-- ============================================================

-- 预置角色
INSERT INTO bot_role (role_code, role_name, description) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限，可管理机器人'),
('ADMIN', '管理员', '可使用管理类指令'),
('USER', '普通用户', '可使用基础指令'),
('READONLY', '只读用户', '只能查看，不能执行修改类操作');

-- 预置系统配置
INSERT INTO bot_system_config (config_key, config_value, description) VALUES
('bot.version', '1.0.0', '机器人版本号'),
('bot.max_dialog_turns', '10', '多轮对话最大轮数'),
('bot.command_cooldown_seconds', '3', '指令冷却时间(秒)');
