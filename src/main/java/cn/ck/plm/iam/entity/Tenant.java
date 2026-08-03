/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

import cn.ck.plm.base.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 租户实体 —— 对应 ck_tenant 表。
 *
 * <p><b>状态流转：</b>
 * <pre>
 * PENDING  →  管理员审核通过  →  ACTIVE  →  SUSPENDED / DISABLED
 * PENDING  →  管理员驳回     →  REJECTED
 * </pre>
 *
 * <p>注册时状态为 PENDING，管理员审核通过后激活为 ACTIVE 并自动创建管理员账号。
 */
public class Tenant extends BaseEntity {

    /** 平台层租户标识 —— 系统配置数据归属此租户，所有业务租户共享 */
    public static final String PLATFORM_TENANT = "platform";

    /** 平台层租户 oid */
    public static final String PLATFORM_TENANT_OID = "00000000-0000-0000-0000-000000000000";
    /** 默认租户 oid */
    public static final String DEFAULT_TENANT_OID = "00000000-0000-0000-0000-000000000001";

    /** 状态常量 */
    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_ACTIVE   = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_DISABLED  = "DISABLED";

    /** 租户标识（唯一，如 T001 / acme-corp） */
    private String tenantId;

    /** 租户/公司名称 */
    private String name;

    /** 状态：PENDING / ACTIVE / REJECTED / SUSPENDED / DISABLED */
    private String status;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人邮箱 */
    private String contactEmail;

    // ==== 申请时提交的管理员信息（审核通过后使用） ====

    /** 管理员用户名 */
    private String adminUsername;

    /** 管理员密码（加密后） */
    private String adminPassword;

    /** 管理员显示名称 */
    private String adminDisplayName;

    // ==== 审核信息 ====

    /** 审核通过时间 */
    private LocalDateTime approvedAt;

    /** 审核人 */
    private String approvedBy;

    /** 驳回原因 */
    private String rejectReason;

    // ==================== 构造方法 ====================

    public Tenant() {
        super();
        this.status = STATUS_PENDING;
    }

    // ==================== 便捷方法 ====================

    public boolean isPending()  { return STATUS_PENDING.equals(status); }
    public boolean isActive()   { return STATUS_ACTIVE.equals(status); }
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }

    // ==================== Getter / Setter ====================

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    @Override
    public String toString() {
        return "Tenant{tenantId='" + tenantId + "', name='" + name + "', status='" + status + "'}";
    }
}
