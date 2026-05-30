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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批提醒服务
 *
 * 入口：
 * - checkAndRemind() → 定时任务：从飞书 API 拉取待审批实例 → 同步到本地 → 发催办提醒
 * - handleWebhookEvent() → Webhook 兜底，处理飞书主动推送的事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalReminderService {

    private final FeishuClient feishuClient;
    private final BotApprovalReminderMapper reminderMapper;
    private final BotUserMapper userMapper;

    @Value("${feishu.review-chat-id:}")
    private String reviewChatId;

    @Value("${feishu.approval-code:}")
    private String approvalCode;

    // ================================================================
    // 入口1：Webhook 兜底（飞书推送事件）
    // ================================================================

    @SuppressWarnings("unchecked")
    public void handleWebhookEvent(String body) {
        try {
            JSONObject root = JSONUtil.parseObj(body);
            JSONObject event = root.getJSONObject("event");
            if (event == null) return;

            String instanceCode = event.getStr("instance_code");
            if (instanceCode == null) return;

            String rootType = root.getStr("type", "");
            String eventType = event.getStr("type", "");

            String approvalName;
            String applicantName;
            String applicantOpenId;

            if ("event_callback".equals(rootType)) {
                approvalName = event.getStr("leave_type", eventType);
                applicantName = event.getStr("leave_reason", "");
                applicantOpenId = event.getStr("open_id", "");
            } else {
                approvalName = event.getStr("approval_name", eventType);
                applicantName = event.getStr("applicant_name", "");
                applicantOpenId = "";
            }

            String status = eventType;
            if ("event_callback".equals(rootType)) {
                status = "PENDING"; // 提交即触发，催领导
            }

            syncInstance(instanceCode, status, approvalName, applicantName, applicantOpenId, event);
        } catch (Exception e) {
            log.error("处理审批 Webhook 事件失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void syncInstance(String instanceCode, String status, String approvalName,
                              String applicantName, String applicantOpenId, JSONObject event) {
        BotApprovalReminder exist = reminderMapper.selectOne(
                new LambdaQueryWrapper<BotApprovalReminder>()
                        .eq(BotApprovalReminder::getApprovalId, instanceCode));

        if (exist != null) {
            int newStatus = "APPROVED".equals(status) ? 2 :
                            "REJECTED".equals(status) ? 3 :
                            "PENDING".equals(status) ? 1 : exist.getStatus();
            if (exist.getStatus() != newStatus) {
                exist.setStatus(newStatus);
                reminderMapper.updateById(exist);
                log.info("审批状态更新 | id={} status={}", instanceCode, newStatus);
            }
            return;
        }

        if (!"PENDING".equals(status)) return;

        BotUser approver = null;
        List<String> approverIdList = event != null ? (List<String>) event.get("approver_id_list") : null;
        if (approverIdList != null && !approverIdList.isEmpty()) {
            approver = userMapper.selectByOpenId(approverIdList.get(0));
        }
        if (approver == null && !applicantOpenId.isBlank()) {
            approver = userMapper.selectByOpenId(applicantOpenId);
        }

        String approverOpenId = (approverIdList != null && !approverIdList.isEmpty())
                ? approverIdList.get(0) : applicantOpenId;

        BotApprovalReminder r = new BotApprovalReminder();
        r.setApprovalId(instanceCode);
        r.setApproverId(approver != null ? approver.getId() : null);
        r.setApproverOpenId(approverOpenId);
        r.setApplicantName(applicantName);
        r.setTitle(approvalName);
        r.setStatus(1);
        r.setRemindCount(0);
        reminderMapper.insert(r);

        log.info("新审批记录 | id={} title={} applicant={} approver={}",
                instanceCode, approvalName, applicantName,
                approver != null ? approver.getName() : "未注册");
    }

    // ================================================================
    // 入口2：定时任务 → 主动拉取飞书审批 + 催办
    // ================================================================

    /** 每 10 秒执行 */
    @Scheduled(cron = "*/10 * * * * *")
    public void checkAndRemind() {
        List<BotApprovalReminder> toRemind = reminderMapper.selectList(
                new LambdaQueryWrapper<BotApprovalReminder>()
                        .eq(BotApprovalReminder::getStatus, 1)
                        .lt(BotApprovalReminder::getRemindCount, 5)
                        .and(w -> w.isNull(BotApprovalReminder::getLastRemindTime)
                                .or().lt(BotApprovalReminder::getLastRemindTime,
                                        LocalDateTime.now().minusSeconds(1))));

        if (toRemind.isEmpty()) {
            return;
        }

        log.info("审批催办：需要提醒 | 数量={}", toRemind.size());
        for (BotApprovalReminder r : toRemind) {
            sendReminder(r);
        }
    }

    /** 发送催办提醒：优先私聊，都失败兜底群聊 */
    private void sendReminder(BotApprovalReminder r) {
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

        boolean sent = false;

        // 1. bot_user 已有记录 → 直接用 bot 上下文的 open_id 发私聊
        BotUser approver = userMapper.selectById(r.getApproverId());
        if (approver != null) {
            try {
                feishuClient.sendTextToUser(approver.getFeishuOpenId(), msg);
                sent = true;
                log.info("审批提醒(私聊-bot) | id={} to={}", r.getApprovalId(), approver.getName());
            } catch (Exception e) {
                log.warn("私聊发送失败(bot) | id={}", r.getApprovalId());
            }
        }

        // 2. approverId 为空 → 查 bot_user 中任意已注册用户发私聊
        if (!sent) {
            BotUser anyUser = userMapper.selectOne(new LambdaQueryWrapper<BotUser>()
                    .isNotNull(BotUser::getFeishuOpenId)
                    .last("LIMIT 1"));
            if (anyUser != null) {
                try {
                    feishuClient.sendTextToUser(anyUser.getFeishuOpenId(), msg);
                    sent = true;
                    log.info("审批提醒(私聊-兜底用户) | id={} to={}", r.getApprovalId(), anyUser.getName());
                } catch (Exception e) {
                    log.warn("私聊发送失败(兜底用户) | id={}", r.getApprovalId());
                }
            }
        }

        // 3. 都没成功 → 发到审查群
        if (!sent && reviewChatId != null && !reviewChatId.isBlank()) {
            try {
                feishuClient.sendTextMessage(reviewChatId, msg);
                sent = true;
                log.info("审批提醒(群聊兜底) | id={} chat={}", r.getApprovalId(), reviewChatId);
            } catch (Exception e) {
                log.error("群聊催办也失败了 | id={}", r.getApprovalId(), e);
            }
        }

        if (!sent) {
            log.warn("审批提醒发送失败 | id={}", r.getApprovalId());
        }

        r.setRemindCount(r.getRemindCount() + 1);
        r.setLastRemindTime(LocalDateTime.now());
        reminderMapper.updateById(r);
    }
}
