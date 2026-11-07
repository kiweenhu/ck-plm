/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.service.impl;

import cn.ck.plm.base.feedback.dto.FeedbackReplyRequest;
import cn.ck.plm.base.feedback.dto.FeedbackRequest;
import cn.ck.plm.base.feedback.entity.Feedback;
import cn.ck.plm.base.feedback.mapper.FeedbackMapper;
import cn.ck.plm.base.feedback.service.api.FeedbackService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.TenantMapper;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 使用反馈实现。
 *
 * <p>两个关键约束：
 * <ul>
 *   <li>租户 / 提交人 / 状态一律由服务端补齐，不信任前端传参；</li>
 *   <li>提交成功后给平台管理员发通知 —— 只有落库没有人看，反馈等于没提。</li>
 * </ul>
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    /** 允许的反馈类型 */
    private static final Set<String> CATEGORIES = Set.of(
            Feedback.CATEGORY_ISSUE, Feedback.CATEGORY_BUG, Feedback.CATEGORY_FEATURE);

    private static final int TITLE_MAX = 200;
    /** 纯文本字数上限（富文本按去标签后的文字算，标签与内嵌图片不计） */
    private static final int CONTENT_MAX = 4000;
    /** HTML 总长上限：富文本可能内嵌 base64 截图，按字符数兜一道，防止提交几 MB 的内容 */
    private static final int HTML_MAX = 500_000;
    private static final int CONTACT_MAX = 200;
    private static final int URL_MAX = 500;
    private static final int CLIENT_MAX = 500;

    /** 回复内容上限（纯文本） */
    private static final int REPLY_MAX = 2000;
    /** 回复后允许置的状态：NEW 是"还没人看"，回复完不能再退回去 */
    private static final Set<String> REPLY_STATUSES = Set.of(
            Feedback.STATUS_PROCESSING, Feedback.STATUS_DONE, Feedback.STATUS_REJECTED);

    private final FeedbackMapper feedbackMapper;
    private final TenantMapper tenantMapper;
    private final UserService userService;
    private final NotificationService notificationService;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper,
                               TenantMapper tenantMapper,
                               UserService userService,
                               NotificationService notificationService) {
        this.feedbackMapper = feedbackMapper;
        this.tenantMapper = tenantMapper;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public String submit(FeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }
        String title = trim(request.getTitle());
        String content = trim(request.getContent());
        if (title == null) {
            throw new IllegalArgumentException("请填写反馈标题");
        }
        if (title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("标题最多 " + TITLE_MAX + " 个字");
        }
        if (content == null) {
            throw new IllegalArgumentException("请描述你遇到的问题或想要的功能");
        }
        if (content.length() > HTML_MAX) {
            throw new IllegalArgumentException("内容过大（截图可能太多），请压缩图片后重试");
        }
        // 富文本按"去标签后的文字"计长度：标签不算，但只贴一张截图也算有效内容
        String plain = plainText(content);
        if (plain.isEmpty()) {
            throw new IllegalArgumentException("请描述你遇到的问题或想要的功能");
        }
        if (plain.length() > CONTENT_MAX) {
            throw new IllegalArgumentException("描述最多 " + CONTENT_MAX + " 个字，当前 " + plain.length() + " 字");
        }

        String category = trim(request.getCategory());
        category = category == null ? Feedback.CATEGORY_ISSUE : category.toUpperCase();
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("反馈类型不合法: " + category);
        }

        Feedback feedback = new Feedback();
        feedback.setCategory(category);
        feedback.setTitle(title);
        feedback.setContent(content);
        feedback.setContact(limit(trim(request.getContact()), CONTACT_MAX, "联系方式"));
        feedback.setPageUrl(limit(trim(request.getPageUrl()), URL_MAX, "页面路径"));
        feedback.setClientInfo(limit(trim(request.getClientInfo()), CLIENT_MAX, "客户端信息"));
        feedback.setStatus(Feedback.STATUS_NEW);
        feedback.setReporterName(UserContext.get());

        // 租户 / 提交人：查得到就存 oid 与展示名，查不到也不阻塞提交
        fillTenant(feedback);
        fillReporter(feedback);

        feedbackMapper.insert(feedback);
        log.info("收到使用反馈: category={}, title={}, tenant={}, reporter={}",
                category, title, feedback.getTenantName(), feedback.getReporterName());

        notifyAdmins(feedback, content);
        return feedback.getOid();
    }

    @Override
    public List<Feedback> listRecent(int limit, String status) {
        String filter = trim(status);
        if (filter != null) {
            filter = filter.toUpperCase();
            if (!filter.equals(Feedback.STATUS_NEW) && !REPLY_STATUSES.contains(filter)) {
                throw new IllegalArgumentException("反馈状态不合法: " + filter);
            }
        }
        return feedbackMapper.selectRecent(limit <= 0 ? 100 : Math.min(limit, 500), filter);
    }

    @Override
    public Feedback detail(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            throw new IllegalArgumentException("反馈标识不能为空");
        }
        Feedback feedback = feedbackMapper.selectByOid(oid.trim());
        if (feedback == null) {
            throw new IllegalArgumentException("反馈不存在或已被删除");
        }
        return feedback;
    }

    /**
     * 本人提交过的反馈。
     *
     * <p>按 reporter_oid 查（提交时服务端写死，前端传不进来）；查不到当前用户也返回空列表 ——
     * 页脚能看到「我的反馈」就够了，不该因为账号数据异常把入口变成报错。
     */
    @Override
    public List<Feedback> listMine(int limit) {
        String username = UserContext.get();
        if (username == null || username.trim().isEmpty()) {
            return List.of();
        }
        User user = userService.findByUsername(username);
        if (user == null || user.getOid() == null) {
            return List.of();
        }
        return feedbackMapper.selectByReporter(user.getOid(), limit <= 0 ? 50 : Math.min(limit, 200));
    }

    /**
     * 回复反馈。
     *
     * <p>回复内容先落库，再发通知 —— 通知发不出去（提交人账号已删、站内信总开关关着）时
     * 回复本身仍然保留，但要把"没送达"如实告诉平台管理员，不能让他以为用户已经看到。
     */
    @Override
    @Transactional
    public boolean reply(String oid, FeedbackReplyRequest request) {
        Feedback feedback = detail(oid);
        if (request == null) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        String reply = trim(request.getReply());
        if (reply == null) {
            throw new IllegalArgumentException("请填写回复内容");
        }
        if (reply.length() > REPLY_MAX) {
            throw new IllegalArgumentException("回复最多 " + REPLY_MAX + " 个字，当前 " + reply.length() + " 字");
        }

        String status = trim(request.getStatus());
        status = status == null ? Feedback.STATUS_DONE : status.toUpperCase();
        if (!REPLY_STATUSES.contains(status)) {
            throw new IllegalArgumentException("回复状态不合法: " + status);
        }

        feedback.setReply(reply);
        feedback.setReplyAt(LocalDateTime.now());
        feedback.setReplierName(UserContext.get());
        feedback.setStatus(status);
        feedbackMapper.updateReply(feedback);

        log.info("反馈已回复: oid={}, status={}, replier={}, title={}",
                feedback.getOid(), status, feedback.getReplierName(), feedback.getTitle());

        return notifyReporter(feedback, reply);
    }

    @Override
    public int countUnprocessed() {
        return feedbackMapper.countUnprocessed();
    }

    // ==================== 内部 ====================

    private void fillTenant(Feedback feedback) {
        String tenantOid = TenantContext.get();
        if (tenantOid == null || tenantOid.trim().isEmpty()) {
            return;
        }
        try {
            Tenant tenant = tenantMapper.selectByOid(tenantOid);
            if (tenant != null) {
                feedback.setTenantId(tenant.getTenantId());
                feedback.setTenantName(tenant.getName());
            }
        } catch (Exception e) {
            log.debug("反馈填写租户信息失败: {}", e.getMessage());
        }
    }

    private void fillReporter(Feedback feedback) {
        try {
            User user = userService.findByUsername(UserContext.get());
            if (user != null) {
                feedback.setReporterOid(user.getOid());
                feedback.setReporterName(user.getUsername());
            }
        } catch (Exception e) {
            log.debug("反馈填写提交人失败: {}", e.getMessage());
        }
    }

    private void notifyAdmins(Feedback feedback, String content) {
        try {
            // 通知里要的是人能读的文字，不能把 HTML 标签原样塞进去
            String plain = plainText(content);
            String excerpt = plain.length() > 120 ? plain.substring(0, 120) + "…" : plain;
            String where = feedback.getTenantName() == null ? "平台" : feedback.getTenantName();
            notificationService.sendToAdmins(
                    "收到使用反馈：" + feedback.getTitle(),
                    "类型：" + categoryLabel(feedback.getCategory())
                            + "｜来自：" + where + " / " + feedback.getReporterName()
                            + (feedback.getPageUrl() == null ? "" : "｜页面：" + feedback.getPageUrl())
                            + "\n" + excerpt,
                    "FEEDBACK",
                    "FEEDBACK",
                    feedback.getOid());
        } catch (Exception e) {
            // 通知失败不能把已经在事务里的反馈回滚掉 —— 用户提交成功才是第一位
            log.warn("反馈通知发送失败: {}", e.getMessage());
        }
    }

    /**
     * 把回复作为站内通知发给提交人。
     *
     * <p>与提交时通知平台管理员走同一条 {@link NotificationService#send}：同样受站内信总开关
     * （{@code plm.notification}）控制，关着时静默跳过。
     *
     * <p>targetType 故意留空：回复通知的落点是「平台管理员 → 问题反馈」页，租户用户点进去
     * 只会看到 403，回复正文直接写在通知里就够了。
     *
     * @return false = 这条反馈没关联到提交人账号（提交时用户已不存在），回复存下了但无处可发
     */
    private boolean notifyReporter(Feedback feedback, String reply) {
        String reporterOid = feedback.getReporterOid();
        if (reporterOid == null || reporterOid.trim().isEmpty()) {
            log.warn("反馈 {} 未关联提交人账号，回复未送达", feedback.getOid());
            return false;
        }
        try {
            notificationService.send(
                    reporterOid,
                    "你的问题反馈已回复：" + feedback.getTitle(),
                    "问题：" + feedback.getTitle()
                            + "\n回复：" + reply
                            + "\n\n如仍有疑问，可继续通过页脚「问题反馈」提交给我们。",
                    "FEEDBACK_REPLY",
                    null,
                    null);
            return true;
        } catch (Exception e) {
            log.warn("反馈回复通知发送失败: {}", e.getMessage());
            return false;
        }
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case Feedback.CATEGORY_BUG -> "缺陷";
            case Feedback.CATEGORY_FEATURE -> "新功能建议";
            default -> "使用问题";
        };
    }

    private String limit(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + "最多 " + max + " 个字符");
        }
        return value;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 富文本 → 纯文本（只用于长度校验与通知摘要）。
     *
     * <p>图片替换成「[图片]」：只贴了一张截图时文字为空，但反馈是有效的，不能当成空内容拦下。
     */
    private String plainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html
                .replaceAll("(?is)<img[^>]*>", "[图片]")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
