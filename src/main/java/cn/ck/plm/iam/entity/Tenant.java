/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
 *
 * <h3>行业初始化（首登引导）</h3>
 * 与 {@code status} 正交的第二条线：{@code onboardingState}（{@code PENDING → DONE}）。
 * 审核通过只代表"租户被允许使用"，还没回答"用它来做什么" —— 行业与研发方法论正是这个答案，
 * 由租户管理员首次登录时在引导页选定（{@link #industryCode} / {@link #devMethodCode}），
 * 完成后记下 {@link #industryPackCode} 与 {@link #onboardedAt}。
 * <b>行业一经选定不可改</b>（要换只能新开租户）—— 因为分类树、阶段、编号规则都会按它落地。
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

    /** 行业初始化状态：待引导 */
    public static final String ONBOARDING_PENDING = "PENDING";
    /** 行业初始化状态：已完成 */
    public static final String ONBOARDING_DONE = "DONE";

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

    /** 联系电话 */
    private String contactPhone;

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

    // ==== 行业与内嵌方法论（行业包引导） ====

    /** 选定行业，如 SMART_HARDWARE / ELECTRONICS_HITECH */
    private String industryCode;

    /** 选定研发方法论，如 IPD / IPD_LITE（板级四阶段） */
    private String devMethodCode;

    /** 实际采用的变体包，如 {@code smart-hardware-ipd@1.0.0} */
    private String industryPackCode;

    /** 引导状态：PENDING（待初始化）/ DONE */
    private String onboardingState;

    /** 完成引导的时间 */
    private LocalDateTime onboardedAt;

    /** 完成引导的人（租户管理员） */
    private String onboardedBy;

    /** 企业画像答案（JSON，驱动推荐与参数建议） */
    private String profileJson;

    // ==================== 构造方法 ====================

    public Tenant() {
        super();
        this.status = STATUS_PENDING;
        // 与库里的 NOT NULL DEFAULT 'PENDING' 保持一致：显式赋值，避免插入 NULL
        this.onboardingState = ONBOARDING_PENDING;
    }

    // ==================== 便捷方法 ====================

    public boolean isPending()  { return STATUS_PENDING.equals(status); }
    public boolean isActive()   { return STATUS_ACTIVE.equals(status); }
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }

    /** 行业初始化是否已完成（未完成时，成员首登会被引导页拦下） */
    public boolean isOnboarded() { return ONBOARDING_DONE.equals(onboardingState); }

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

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

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

    public String getIndustryCode() { return industryCode; }
    public void setIndustryCode(String industryCode) { this.industryCode = industryCode; }

    public String getDevMethodCode() { return devMethodCode; }
    public void setDevMethodCode(String devMethodCode) { this.devMethodCode = devMethodCode; }

    public String getIndustryPackCode() { return industryPackCode; }
    public void setIndustryPackCode(String industryPackCode) { this.industryPackCode = industryPackCode; }

    public String getOnboardingState() { return onboardingState; }
    public void setOnboardingState(String onboardingState) { this.onboardingState = onboardingState; }

    public LocalDateTime getOnboardedAt() { return onboardedAt; }
    public void setOnboardedAt(LocalDateTime onboardedAt) { this.onboardedAt = onboardedAt; }

    public String getOnboardedBy() { return onboardedBy; }
    public void setOnboardedBy(String onboardedBy) { this.onboardedBy = onboardedBy; }

    public String getProfileJson() { return profileJson; }
    public void setProfileJson(String profileJson) { this.profileJson = profileJson; }

    @Override
    public String toString() {
        return "Tenant{tenantId='" + tenantId + "', name='" + name + "', status='" + status
                + "', onboarding='" + onboardingState + "'}";
    }
}
