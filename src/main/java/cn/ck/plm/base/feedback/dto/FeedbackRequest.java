/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.dto;

/**
 * 提交反馈的入参（页脚「问题反馈」弹窗）。
 *
 * <p>租户、提交人、状态均由服务端补齐 —— 不接受前端传入，避免伪造。
 */
public class FeedbackRequest {

    /** ISSUE / BUG / FEATURE */
    private String category;

    /** 一句话标题 */
    private String title;

    /** 详细描述 */
    private String content;

    /** 联系方式，可为空 */
    private String contact;

    /** 提交时所在页面路径 */
    private String pageUrl;

    /** 客户端信息（浏览器 / 分辨率），可为空 */
    private String clientInfo;

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

    public String getClientInfo() { return clientInfo; }
    public void setClientInfo(String clientInfo) { this.clientInfo = clientInfo; }
}
