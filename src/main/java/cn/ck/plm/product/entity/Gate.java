/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * 阶段门实例：某个产品系列 / 型号自己要过的那道门（TR1–TR6 技术评审、Charter/PDCP/ADCP/LDCP 决策门）。
 *
 * <p>创建时从阶段门模板（{@code ck_gate_template}）快照而来，与 {@link Stage} 同一套归属方式。
 *
 * <h3>两套状态别混</h3>
 * {@code disabled} = 本项目裁剪；{@code status} / {@code decision} = 执行态（过没过、谁在何时拍的板）。
 * 决策留痕要经得起审厂 —— 故 {@code decidedBy} / {@code decidedAt} / {@code decisionNote} 必须在实例上。
 */
public class Gate extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE / MODEL（见 {@link Stage#OWNER_TYPE_LINE}） */
    private String ownerType;

    /** 所属研发阶段 code（↔ ck_stage.code） */
    private String stageKey;

    /** 门类型（TECHNICAL / DECISION…） */
    private String type;

    /** 关联流程 key（评审 / 决策流程，来自基础包） */
    private String processKey;

    /** 决策选项（JSON 字符串） */
    private String decisionOptions;

    /** 自动检查项（JSON 字符串） */
    private String autoChecks;

    /** 必须到场的角色（JSON 字符串） */
    private String requiredParticipants;

    /** 留痕字段要求（JSON 字符串） */
    private String recordFields;

    /** 是否要求证据（与模板同型：模板该列即 BOOLEAN） */
    private Boolean evidenceRequired;

    private String industryNote;

    /** 溯源：从哪条模板快照而来 */
    private String templateCode;

    /** 执行态：PENDING / READY / PASSED / REJECTED */
    private String status;

    /** 实际决策结论 */
    private String decision;

    /** 决策人 */
    private String decidedBy;

    /** 决策时间 */
    private java.time.LocalDateTime decidedAt;

    /** 决策说明 */
    private String decisionNote;

    /** 本项目裁剪：停用 */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    // ==================== 流程进展（传输字段，不落库）====================
    // 与 Review 上同名字段同一口径：由 GateProcessEvaluator 在返回前现算填入，
    // 因为 ck_gate.status 是快照值 —— 发起流程后没有任何人写它，行上会一直停在「未开始」。

    /** 当前认领到这条门的流程实例 id（点击「查看流程」用） */
    private String processInstanceId;

    /** 该实例状态：running / suspended / completed / terminated（见 ProcessInstanceVO） */
    private String processStatus;

    /** 运行中实例的当前节点名（多个用「、」连接） */
    private String currentActivityName;

    /** 当前节点办理人（未指派为空串） */
    private String currentAssignees;

    /** 流程发起人显示名 */
    private String processStartUserName;

    /** 流程开始时间 */
    private java.time.LocalDateTime processStartedAt;

    /** 流程结束时间（未结束为空） */
    private java.time.LocalDateTime processEndedAt;

    public Gate() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }

    public String getDecisionOptions() { return decisionOptions; }
    public void setDecisionOptions(String decisionOptions) { this.decisionOptions = decisionOptions; }

    public String getAutoChecks() { return autoChecks; }
    public void setAutoChecks(String autoChecks) { this.autoChecks = autoChecks; }

    public String getRequiredParticipants() { return requiredParticipants; }
    public void setRequiredParticipants(String requiredParticipants) { this.requiredParticipants = requiredParticipants; }

    public String getRecordFields() { return recordFields; }
    public void setRecordFields(String recordFields) { this.recordFields = recordFields; }

    public Boolean getEvidenceRequired() { return evidenceRequired; }
    public void setEvidenceRequired(Boolean evidenceRequired) { this.evidenceRequired = evidenceRequired; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public java.time.LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(java.time.LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }

    public String getCurrentActivityName() { return currentActivityName; }
    public void setCurrentActivityName(String currentActivityName) { this.currentActivityName = currentActivityName; }

    public String getCurrentAssignees() { return currentAssignees; }
    public void setCurrentAssignees(String currentAssignees) { this.currentAssignees = currentAssignees; }

    public String getProcessStartUserName() { return processStartUserName; }
    public void setProcessStartUserName(String processStartUserName) { this.processStartUserName = processStartUserName; }

    public java.time.LocalDateTime getProcessStartedAt() { return processStartedAt; }
    public void setProcessStartedAt(java.time.LocalDateTime processStartedAt) { this.processStartedAt = processStartedAt; }

    public java.time.LocalDateTime getProcessEndedAt() { return processEndedAt; }
    public void setProcessEndedAt(java.time.LocalDateTime processEndedAt) { this.processEndedAt = processEndedAt; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
