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
 * 变更请求（ECR）—— 回答"<b>为什么要改</b>"。
 *
 * <h3>为什么不做版本控制</h3>
 * <p>参考 Windchill 的 Change Request：变更单本身不走 Revision/Iteration，而是
 * <b>状态机 + 修订留痕</b>。变更单的价值在"结论与追溯链"，不在"同一张单的多个版本"；
 * 一旦版本化，它的 revise 语义就会与它要驱动的对象 revise 撞车。
 * 真正被修订的是受影响对象（见 {@link ChangeAffectedObject}）。
 *
 * <h3>与其它对象的关系</h3>
 * <pre>
 * Issue（问题）  1 ── N  ChangeRequest   (通过 sourceIssueOid，可选)
 * ChangeRequest  1 ── N  ChangeNotice    (通过 requestOid，可选 —— ECN 允许直接开)
 * </pre>
 *
 * <p>状态取值见 {@link cn.ck.plm.change.flow.ChangeRequestFlow}。
 */
public class ChangeRequest extends WithoutVersionEntity implements TenantEntity {

    /** 变更类型：DESIGN / DOCUMENT / BOM / PROCESS / MATERIAL；空 = 未分类 */
    private String changeType;

    /** 优先级：HIGH / MEDIUM / LOW */
    private String priority;

    /**
     * 变更分级：QUICK 快速 / STANDARD 标准 / MAJOR 重大；空 = STANDARD。
     *
     * <p>它不只是一枚标签：评审流程按它分流审签路径（快速 = 单人审批，标准 = 影响分析会签 +
     * 产品经理审批，重大 = 影响分析会签 + CCB 会签）。因此一旦发起评审就不该再改 ——
     * 改了会让在途实例走的是旧路径、单据上写的是新分级。
     */
    private String changeLevel;

    /** 认证 / 合规影响：NONE 无 / REVIEW 需评估重测 / YES 需重新认证；空 = 未评估 */
    private String certImpact;

    /** 客户 / 合同影响：NONE 无 / NOTIFY 需知会 / APPROVAL 需客户批准；空 = 未评估 */
    private String customerImpact;

    /** 在制与库存影响：NONE 无 / PENDING 待在制盘点 / DISPOSAL 需报废返工；空 = 未评估 */
    private String inventoryImpact;

    /** 变更原因（提交时必填 —— 说不出"为什么"的变更不该进入评审） */
    private String reason;

    /** 建议方案 */
    private String proposal;

    /** 状态（见 ChangeRequestFlow） */
    private String status;

    /** 来源问题（ck_issue.oid）；可选 */
    private String sourceIssueOid;

    /** 关联产品系列/型号 oid */
    private String productOid;

    /** 产品类型：PRODUCT_LINE / PRODUCT_MODEL */
    private String productType;

    /**
     * 所属容器名称（<b>传输字段，不落库</b>）。
     *
     * <p>列表上只给 productOid，用户看到的是一串 uuid；由查询按 oid 反查
     * ck_product_model / ck_product_line 得到，见 {@code PostgreSqlChangeRequestMapper#SELECT_COLUMNS}。
     */
    private String productName;

    /**
     * 已绑定变更通告的编号（<b>传输字段，不落库</b>）。
     *
     * <p>变更通告表单的「来源变更请求」下拉原先列出全部请求，已经派发过通告的那条
     * 仍能被再选一次。这里按 {@code ck_change_notice.request_oid} 反查一条通告编号
     * （见 {@code PostgreSqlChangeRequestMapper#SELECT_COLUMNS}），前端据此把该请求
     * 置灰：判"是否被占用"只看非空，编号用于在选项上说明被谁占了。
     *
     * <p>已取消（CANCELLED）的通告不算占用 —— 单据作废后请求应当能重新派发。
     */
    private String noticeCode;

    /** 责任人（用户名） */
    private String owner;

    /** 提出人（用户名，创建时自动 = 当前用户） */
    private String requester;

    /** 计划完成日期 */
    private LocalDate dueDate;

    /** 受理时间（ACCEPT 时填） */
    private LocalDateTime acceptedAt;

    /** 关闭时间（CLOSED 时填） */
    private LocalDateTime closedAt;

    /**
     * 评审流程实例 id（表 {@code ck_change_request.process_instance_id}）。
     *
     * <p>只有「审批开启」时由「发起评审」写入；流程结束由
     * {@code ChangeRequestReviewOutcomeHandler} 按 {@code approved} 回写
     * {@code ACCEPTED / REJECTED}。为空 = 未走流程（免审或历史单据）。
     */
    private String processInstanceId;

    /** 租户 oid */
    private String tenantOid;

    /**
     * 补充材料（表 {@code ck_change_request.attachment_oid}，指向 {@code ck_attachment}）。
     *
     * <p>评审常需要客户确认函、测试报告、图纸截图这类佐证材料：单据在「草稿 / 已提交」时
     * 允许补传。为什么存单个 oid 而不是附件表：与 PART / DOCUMENT 的 attachmentOid 同一口径 ——
     * 上传发生在单据保存之前，先把 oid 拿在手里，保存时随单据落库（见 {@code FileUploader}）。
     */
    private String attachmentOid;

    /**
     * 关联问题 oid 列表（<b>传输字段，不落库</b>）。
     *
     * <p>为什么放在实体上而不是单独包一层命令对象：本接口一直直接收发 {@link ChangeRequest}，
     * 为了一个列表参数改前端全部字段的嵌套层级不值得。{@code null} 与空列表语义不同 ——
     * 前者表示"这次不改关联"（patch 语义），后者表示"清空关联"。
     *
     * <p>真正的持久化在 {@code ck_change_request_issue}，见 {@link ChangeRequestIssue}。
     */
    private List<String> issueOids;

    /**
     * 预估受影响对象的 oid 列表（<b>传输字段，不落库</b>，与 {@link #issueOids} 同风格）。
     *
     * <p>{@code null} = 这次不改；空列表 = 清空。对象类型 / 编码 / 名称 / 所属容器
     * 一律由服务端按 oid 回查对象主表得到，不接受前端传值（防篡改、防漏填）。
     */
    private List<String> affectedObjectOids;

    /** 预估受影响对象明细（<b>只读返回</b>，来自 ck_change_request_affected_object） */
    private List<ChangeRequestAffectedObject> affectedObjects;

    public ChangeRequest() {
    }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getChangeLevel() { return changeLevel; }
    public void setChangeLevel(String changeLevel) { this.changeLevel = changeLevel; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceIssueOid() { return sourceIssueOid; }
    public void setSourceIssueOid(String sourceIssueOid) { this.sourceIssueOid = sourceIssueOid; }

    public String getProductOid() { return productOid; }
    public void setProductOid(String productOid) { this.productOid = productOid; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getNoticeCode() { return noticeCode; }
    public void setNoticeCode(String noticeCode) { this.noticeCode = noticeCode; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getRequester() { return requester; }
    public void setRequester(String requester) { this.requester = requester; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public List<String> getIssueOids() { return issueOids; }

    public void setIssueOids(List<String> issueOids) { this.issueOids = issueOids; }

    public List<String> getAffectedObjectOids() { return affectedObjectOids; }
    public void setAffectedObjectOids(List<String> affectedObjectOids) { this.affectedObjectOids = affectedObjectOids; }

    public List<ChangeRequestAffectedObject> getAffectedObjects() { return affectedObjects; }
    public void setAffectedObjects(List<ChangeRequestAffectedObject> affectedObjects) { this.affectedObjects = affectedObjects; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getAttachmentOid() { return attachmentOid; }
    public void setAttachmentOid(String attachmentOid) { this.attachmentOid = attachmentOid; }

    @Override
    public String toString() {
        return "ChangeRequest{code='" + getCode() + "', status='" + status + "'}";
    }
}
