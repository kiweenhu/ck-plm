/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.entity;

import cn.ck.plm.base.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 使用反馈 —— 用户在页脚「问题反馈」提交的问题 / 缺陷 / 新功能建议。
 *
 * <p>表 {@code ck_feedback} 刻意<b>不参与租户隔离</b>（见 TenantStatementInterceptor 的
 * SHARED 名单）：平台管理员要跨租户统一查看处理，租户信息只作为文本冗余存一份。
 * 因此本实体也<b>不要</b>加 {@code tenantOid} 字段 —— 表里没有这一列。
 */
public class Feedback extends BaseEntity {

    /** 使用问题（功能不会用 / 结果不符合预期） */
    public static final String CATEGORY_ISSUE = "ISSUE";
    /** 缺陷（报错、白屏、数据错乱等明确 bug） */
    public static final String CATEGORY_BUG = "BUG";
    /** 新功能建议 */
    public static final String CATEGORY_FEATURE = "FEATURE";

    /** 待处理 */
    public static final String STATUS_NEW = "NEW";
    /** 跟进中（已看过、还没给出结论） */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 已回复 */
    public static final String STATUS_DONE = "DONE";
    /** 不采纳 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 反馈类型：ISSUE / BUG / FEATURE */
    private String category;

    /** 一句话标题 */
    private String title;

    /** 详细描述 */
    private String content;

    /** 联系方式（邮箱 / 微信 / 电话），可为空 */
    private String contact;

    /** 提交时所在页面路径，便于复现 */
    private String pageUrl;

    /** 租户标识（文本冗余，不参与隔离） */
    private String tenantId;

    /** 租户名称（文本冗余） */
    private String tenantName;

    /** 提交人 oid */
    private String reporterOid;

    /** 提交人登录名 */
    private String reporterName;

    /** 客户端信息（浏览器 / 分辨率 / 版本） */
    private String clientInfo;

    /** 处理状态：NEW / PROCESSING / DONE / REJECTED */
    private String status;

    /** 平台管理员的回复（纯文本），未回复时为 null */
    private String reply;

    /** 回复时间 */
    private LocalDateTime replyAt;

    /** 回复人登录名 */
    private String replierName;

    // ==================== Getter / Setter ====================

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getReporterOid() { return reporterOid; }
    public void setReporterOid(String reporterOid) { this.reporterOid = reporterOid; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getClientInfo() { return clientInfo; }
    public void setClientInfo(String clientInfo) { this.clientInfo = clientInfo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public LocalDateTime getReplyAt() { return replyAt; }
    public void setReplyAt(LocalDateTime replyAt) { this.replyAt = replyAt; }

    public String getReplierName() { return replierName; }
    public void setReplierName(String replierName) { this.replierName = replierName; }
}
