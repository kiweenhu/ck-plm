/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.dto;

/**
 * 平台管理员回复反馈的入参。
 *
 * <p>回复人、回复时间一律由服务端补齐，不接受前端传入。
 */
public class FeedbackReplyRequest {

    /** 回复内容（纯文本） */
    private String reply;

    /** 回复后置为的处理状态：PROCESSING（跟进中）/ DONE（已回复），不填默认 DONE */
    private String status;

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
