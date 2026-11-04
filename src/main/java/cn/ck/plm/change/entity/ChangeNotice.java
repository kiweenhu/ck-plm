/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更通告（ECN）—— 回答"<b>改什么、谁授权改</b>"。
 *
 * <h3>与 ECR 的关系</h3>
 * <p>{@link #requestOid} <b>可空</b>：Windchill 强制 CR→CN，但中小场景常有"直接开 ECN"。
 *
 * <p>派生时**复制一份定性信息的快照**（见 {@link #changeType} 等），不是引用：
 * ECR 是判定书（分级 / 影响结论可能经过会签与 CCB），ECN 是执行令 —— 执行令要能独立说清
 * "为什么改、多严重、影响什么"，同时不该在事后跟着 ECR 一起变（审批人当时批的是那份内容）。
 * 表单上这些列在派生单里只读，判定只在 ECR 上做一次。
 *
 * <h3>审批</h3>
 * <p>审批委托给流程引擎：{@link #processInstanceId} 指向 Flowable 实例，流程结束时由
 * {@code ProcessOutcomeHandler} 回写状态。未配流程的租户可开"免审"：SUBMIT 时直接置
 * {@code APPROVED}，但同时写 {@link #approvalBypassed} / {@link #bypassReason} ——
 * <b>"免审"必须是看得见的，不能变成默认行为</b>。
 *
 * <p>状态取值见 {@link cn.ck.plm.change.flow.ChangeNoticeFlow}。
 */
public class ChangeNotice extends WithoutVersionEntity implements TenantEntity {

    /** 来源变更请求（ck_change_request.oid）；可空 */
    private String requestOid;

    /**
     * 所属容器（产品系列 / 型号 oid）。
     *
     * <p>ECN 允许独立于 ECR 存在，所以产品归属不能只靠来源请求继承 ——
     * 直接开的通告同样要能说清"这次改的是哪个产品"。
     */
    private String productOid;

    /** 容器类型：PRODUCT_LINE / PRODUCT_MODEL */
    private String productType;

    /**
     * 变更类型：DESIGN / DOCUMENT / BOM / PROCESS / MATERIAL。
     *
     * <p>派生单里是创建时从 ECR 复制来的快照，表单上只读；独立 ECN 由填单人给。
     */
    private String changeType;

    /** 变更分级：QUICK / STANDARD / MAJOR（同一套取值，判定口径见 ECR） */
    private String changeLevel;

    /** 优先级：HIGH / MEDIUM / LOW */
    private String priority;

    /** 认证 / 合规影响：NONE / REVIEW / YES；空 = 未评估 */
    private String certImpact;

    /** 客户 / 合同影响：NONE / NOTIFY / APPROVAL；空 = 未评估 */
    private String customerImpact;

    /** 在制 / 库存影响：NONE / PENDING / DISPOSAL；空 = 未评估 */
    private String inventoryImpact;

    /** 变更原因（独立 ECN 必填；派生单从 ECR 带过来） */
    private String reason;

    /**
     * 执行方案。
     *
     * <p>派生单以 ECR 的「建议方案」为起点（可改）：ECR 说的是"打算怎么改"，
     * 执行令要落到"这次具体怎么做"。与 {@link #reason} 的区别是它**不是**判定结论，
     * 所以派生单里仍然可编辑。
     */
    private String proposal;

    /**
     * 所属容器名称（<b>传输字段，不落库</b>）。
     *
     * <p>列表上只给 oid，用户看到的是一串 uuid。由查询按 product_oid 反查
     * ck_product_model / ck_product_line 得到，见 {@code PostgreSqlChangeNoticeMapper#SELECT_COLUMNS}。
     */
    private String productName;

    /** 状态（见 ChangeNoticeFlow） */
    private String status;

    /** 计划开始日期 */
    private LocalDate planStart;

    /** 计划完成日期 */
    private LocalDate planEnd;

    /** 绑定的流程实例 id（Flowable） */
    private String processInstanceId;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 实施完成时间（所有受影响对象都执行完） */
    private LocalDateTime completedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 责任人（用户名） */
    private String owner;

    /** 是否免审通过（审计标记：为 true 时本次变更没有走审批流程） */
    private boolean approvalBypassed;

    /** 免审原因（免审时必填） */
    private String bypassReason;

    /** 免审操作人 */
    private String bypassedBy;

    /** 免审时间 */
    private LocalDateTime bypassedAt;

    /** 租户 oid */
    private String tenantOid;

    /**
     * 补充材料（表 {@code ck_change_notice.attachment_oid}，指向 {@code ck_attachment}）。
     *
     * <p>通告是执行单：返工方案、试产报告、供应商确认这类材料需要随单留存。
     * 单个 oid 的口径与 {@link ChangeRequest#getAttachmentOid()}、PART / DOCUMENT 一致。
     */
    private String attachmentOid;

    /**
     * 关联问题 oid 列表（<b>传输字段，不落库</b>）。
     *
     * <p>{@code null} 与空列表语义不同：前者表示"这次不改关联"（patch 语义），
     * 后者表示"清空"。创建时若为 {@code null} 且填了 {@link #requestOid}，
     * 会把来源变更请求的关联问题带过来。
     */
    private List<String> issueOids;

    /**
     * 创建时指定的变更对象（<b>传输字段，不落库</b>）。
     *
     * <p>提交的是对象 oid 列表：类型 / 编码 / 名称 / 所属容器与 <b>受影响版本</b>
     * 都由服务端回查（版本取对象当前 latest 迭代）。不传则按来源请求的预估对象带入。
     * 编辑时不接受该字段 —— 受影响对象在详情里增删，避免整表重建把任务/执行状态冲掉。
     */
    private List<String> affectedObjectOids;

    public ChangeNotice() {
    }

    public String getRequestOid() { return requestOid; }
    public void setRequestOid(String requestOid) { this.requestOid = requestOid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getPlanStart() { return planStart; }
    public void setPlanStart(LocalDate planStart) { this.planStart = planStart; }

    public LocalDate getPlanEnd() { return planEnd; }
    public void setPlanEnd(LocalDate planEnd) { this.planEnd = planEnd; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getProductOid() { return productOid; }
    public void setProductOid(String productOid) { this.productOid = productOid; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getChangeLevel() { return changeLevel; }
    public void setChangeLevel(String changeLevel) { this.changeLevel = changeLevel; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCertImpact() { return certImpact; }
    public void setCertImpact(String certImpact) { this.certImpact = certImpact; }

    public String getCustomerImpact() { return customerImpact; }
    public void setCustomerImpact(String customerImpact) { this.customerImpact = customerImpact; }

    public String getInventoryImpact() { return inventoryImpact; }
    public void setInventoryImpact(String inventoryImpact) { this.inventoryImpact = inventoryImpact; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getProposal() { return proposal; }
    public void setProposal(String proposal) { this.proposal = proposal; }

    public boolean isApprovalBypassed() { return approvalBypassed; }
    public void setApprovalBypassed(boolean approvalBypassed) { this.approvalBypassed = approvalBypassed; }

    public String getBypassReason() { return bypassReason; }
    public void setBypassReason(String bypassReason) { this.bypassReason = bypassReason; }

    public String getBypassedBy() { return bypassedBy; }
    public void setBypassedBy(String bypassedBy) { this.bypassedBy = bypassedBy; }

    public LocalDateTime getBypassedAt() { return bypassedAt; }
    public void setBypassedAt(LocalDateTime bypassedAt) { this.bypassedAt = bypassedAt; }

    public List<String> getAffectedObjectOids() { return affectedObjectOids; }
    public void setAffectedObjectOids(List<String> affectedObjectOids) { this.affectedObjectOids = affectedObjectOids; }

    public List<String> getIssueOids() { return issueOids; }

    public void setIssueOids(List<String> issueOids) { this.issueOids = issueOids; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getAttachmentOid() { return attachmentOid; }
    public void setAttachmentOid(String attachmentOid) { this.attachmentOid = attachmentOid; }

    @Override
    public String toString() {
        return "ChangeNotice{code='" + getCode() + "', status='" + status + "'}";
    }
}
