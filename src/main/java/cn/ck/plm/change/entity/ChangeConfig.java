/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import java.time.LocalDateTime;

/**
 * 变更租户配置（表 {@code ck_change_config}，主键 = tenant_oid）。
 *
 * <h3>免审开关的口径</h3>
 * <p>{@link #approvalRequired} 默认 <b>true</b>：免审是显式选择，不是默认行为。
 * 关掉它之后，ECN 的 SUBMIT 会直接置 {@code APPROVED}，但同时写
 * {@code ck_change_notice.approval_bypassed = true} + 免审原因 ——
 * 审计必须能看出"这次没有走审批"，而不是让免审变成无声的常态。
 *
 * <p>{@link #bypassForReleased} 默认 <b>false</b>：免审时也不允许改"已发布/已归档"的对象。
 * 免审是为了少一道流程，不是为了绕过"已发布版本不可动"这条更底层的纪律。
 *
 * <p>不继承 BaseEntity（主键不是 oid），审计列由服务层显式维护。
 */
public class ChangeConfig {

    /** 租户 oid（主键） */
    private String tenantOid;

    /** 是否必须审批（false = 免审） */
    private boolean approvalRequired = true;

    /**
     * 变更请求（ECR）流程定义 key。
     *
     * <p>内置包提供 {@code change-request}（顶层：提出 → 设置审批人 → 变更审批 → 派发通告）。
     * 用户自定义流程后可改指自己那张。
     */
    private String requestProcessKey = "change-request";

    /**
     * 变更通告（ECN）审批流程定义 key。
     *
     * <p><b>当前唯一已接入流程引擎的入口</b>：ECN 的「发起审批」按它
     * {@code startProcess}（见 {@code ChangeNoticeServiceImpl}）。
     */
    private String approvalProcessKey = "change-notice";

    /**
     * 变更任务（ECT）流程定义 key。
     *
     * <p>内置包提供 {@code change-task}（任务下达 → 执行 → 完成）。
     */
    private String taskProcessKey = "change-task";

    /** 免审时是否允许修订"已发布/已归档"的对象 */
    private boolean bypassForReleased;

    /**
     * 是否启用变更请求（ECR）。
     *
     * <p>默认 <b>true</b>。关掉之后「变更控制」不再出现「变更请求」页签 —— 只走变更通告的团队
     * （改动物料直接开 ECN）不必看着一个永远没单的页签。这是**入口开关**：不清存量 ECR、
     * 不改历史单据的流转，也不拦服务层接口（流程里派生的 ECR 仍能正常读写）。
     */
    private boolean requestEnabled = true;

    /** 最后修改人 */
    private String updater;

    /** 最后修改时间 */
    private LocalDateTime updatedAt;

    public ChangeConfig() {
    }

    public String getTenantOid() { return tenantOid; }
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }

    public String getRequestProcessKey() { return requestProcessKey; }
    public void setRequestProcessKey(String requestProcessKey) { this.requestProcessKey = requestProcessKey; }

    public String getApprovalProcessKey() { return approvalProcessKey; }
    public void setApprovalProcessKey(String approvalProcessKey) { this.approvalProcessKey = approvalProcessKey; }

    public String getTaskProcessKey() { return taskProcessKey; }
    public void setTaskProcessKey(String taskProcessKey) { this.taskProcessKey = taskProcessKey; }

    public boolean isBypassForReleased() { return bypassForReleased; }
    public void setBypassForReleased(boolean bypassForReleased) { this.bypassForReleased = bypassForReleased; }

    public boolean isRequestEnabled() { return requestEnabled; }
    public void setRequestEnabled(boolean requestEnabled) { this.requestEnabled = requestEnabled; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
