package com.bluemountain.bot.core.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluemountain.bot.infrastructure.entity.BotApprovalReminder;
import com.bluemountain.bot.infrastructure.entity.BotUser;
import com.bluemountain.bot.infrastructure.mapper.BotApprovalReminderMapper;
import com.bluemountain.bot.infrastructure.mapper.BotUserMapper;
import com.bluemountain.bot.integration.client.FeishuClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批提醒服务
 *
 * 两个入口：
 * 1. handleWebhookEvent() → 飞书推送审批状态变更时调用，写 bot_approval_reminder 表
 * 2. checkAndRemind()    → 定时任务，查表筛选该催的 + 发私聊提醒
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalReminderService {

    private final FeishuClient feishuClient;
    private final BotApprovalReminderMapper reminderMapper;
    private final BotUserMapper userMapper;

    // ================================================================
    // 入口1：飞书 Webhook 推送审批事件 → 写入/更新 bot_approval_reminder
    // ================================================================

    /**
     * 处理飞书推送的审批实例事件
     *
     * 飞书推送 JSON 示例：
     * {
     *   "header": {"event_type": "approval_instance"},
     *   "event": {
     *     "type": "PENDING",              // PENDING / APPROVED / REJECTED
     *     "instance_code": "7CACB4B6-xxx",
     *     "approval_name": "请假",
     *     "applicant_name": "张三",
     *     "approver_id_list": ["ou_yyy"],
     *     "start_time": "1716110000000"
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    public void handleWebhookEvent(String body) {
        try {
            JSONObject root = JSONUtil.parseObj(body);
            JSONObject event = root.getJSONObject("event");
            if (event == null) return;

            String status = event.getStr("type", "");  // PENDING / APPROVED / REJECTED
            String instanceCode = event.getStr("instance_code");
            String approvalName = event.getStr("approval_name", "");
            String applicantName = event.getStr("applicant_name", "");

            if (instanceCode == null) return;

            // 查是否已存在
            BotApprovalReminder exist = reminderMapper.selectOne(
                    new LambdaQueryWrapper<BotApprovalReminder>()
                            .eq(BotApprovalReminder::getApprovalId, instanceCode));

            if (exist != null) {
                // 状态变更：PENDING → APPROVED / REJECTED
                int newStatus = "APPROVED".equals(status) ? 2 :
                                "REJECTED".equals(status) ? 3 : exist.getStatus();
                if (exist.getStatus() != newStatus) {
                    exist.setStatus(newStatus);
                    reminderMapper.updateById(exist);
                    log.info("审批状态更新 | id={} status={}", instanceCode, status);
                }
                return;
            }

            // 新审批单 → 只记录待审批的
            if (!"PENDING".equals(status)) return;

            // 取审批人 open_id
            List<String> approverIdList = (List<String>) event.get("approver_id_list");
            if (approverIdList == null || approverIdList.isEmpty()) return;
            String approverOpenId = approverIdList.get(0);

            BotUser approver = userMapper.selectByOpenId(approverOpenId);

            BotApprovalReminder r = new BotApprovalReminder();
            r.setApprovalId(instanceCode);
            r.setApproverId(approver != null ? approver.getId() : null);
            r.setApplicantName(applicantName);
            r.setTitle(approvalName);
            r.setStatus(1);
            r.setRemindCount(0);
            reminderMapper.insert(r);

            log.info("新审批记录 | id={} title={} applicant={} approver={}",
                    instanceCode, approvalName, applicantName,
                    approver != null ? approver.getName() : "未注册");

        } catch (Exception e) {
            log.error("处理审批 Webhook 事件失败", e);
        }
    }

    // ================================================================
    // 入口2：定时任务 → 查表 + 催办
    // ================================================================

    /** 每 10 秒执行（测试用，上线改回 5 分钟） */
    @Scheduled(fixedRate = 10000)
    public void checkAndRemind() {
        // 筛出该催的：待审批 + 提醒次数 < 5 + 距上次提醒 > 30 秒（测试，上线改回 1 小时）
        List<BotApprovalReminder> toRemind = reminderMapper.selectList(
                new LambdaQueryWrapper<BotApprovalReminder>()
                        .eq(BotApprovalReminder::getStatus, 1)
                        .lt(BotApprovalReminder::getRemindCount, 5)
                        .and(w -> w.isNull(BotApprovalReminder::getLastRemindTime)
                                .or().lt(BotApprovalReminder::getLastRemindTime,
                                        LocalDateTime.now().minusSeconds(30))));

        if (toRemind.isEmpty()) {
            log.debug("审批催办：无需提醒");
            return;
        }

        log.info("审批催办：需要提醒 | 数量={}", toRemind.size());
        for (BotApprovalReminder r : toRemind) {
            sendReminder(r);
        }
    }

    /** 给审批人发私聊催办消息 */
    private void sendReminder(BotApprovalReminder r) {
        BotUser approver = userMapper.selectById(r.getApproverId());
        if (approver == null) {
            log.warn("审批人未注册 | approverId={}", r.getApproverId());
            return;
        }

        String msg = String.format("""
                ⚠ **审批提醒**

                你有一条待审批：**%s**
                申请人：%s
                提交时间：%s

                这是第 %d 次提醒，请及时处理""",
                r.getTitle(),
                r.getApplicantName(),
                r.getCreateTime() != null
                        ? r.getCreateTime().toString().replace("T", " ") : "-",
                r.getRemindCount() + 1);

        feishuClient.sendTextToUser(approver.getFeishuOpenId(), msg);

        r.setRemindCount(r.getRemindCount() + 1);
        r.setLastRemindTime(LocalDateTime.now());
        reminderMapper.updateById(r);

        log.info("审批提醒已发送 | id={} to={} count={}",
                r.getApprovalId(), approver.getName(), r.getRemindCount());
    }
}
