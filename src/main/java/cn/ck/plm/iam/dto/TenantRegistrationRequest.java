/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.dto;

/**
 * 租户注册请求 DTO —— 登录页面"注册租户"表单提交数据。
 *
 * <p>注册时同步创建租户和该租户的管理员账号。
 */
public class TenantRegistrationRequest {

    /** 租户标识（唯一，用于数据隔离，如 T001 / acme-corp） */
    private String tenantId;

    /** 租户/公司名称 */
    private String name;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 管理员用户名（登录账号） */
    private String adminUsername;

    /** 管理员密码 */
    private String adminPassword;

    /** 管理员显示名称 */
    private String adminDisplayName;

    // ==================== Getter / Setter ====================

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getAdminDisplayName() { return adminDisplayName; }
    public void setAdminDisplayName(String adminDisplayName) { this.adminDisplayName = adminDisplayName; }
}
