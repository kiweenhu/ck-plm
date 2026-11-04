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
 * 评审实例：某个产品系列 / 型号要开的那场评审（原理图 / 概念与需求 / 规格与架构 / 结构 /
 * 软件固件 / 验证与认证 / 量产就绪 / 生命周期复盘…）。
 *
 * <p>创建时从评审模板（{@code ck_review_template}）快照而来；{@code gate} 指向它服务于哪道门
 * （评审实例与阶段门实例通过这一列关联，门靠"本阶段交付件完成率"判定）。
 */
public class Review extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE / MODEL（见 {@link Stage#OWNER_TYPE_LINE}） */
    private String ownerType;

    /** 归属于哪个阶段（↔ ck_stage.code）；技术评审直接挂阶段，2026-10-29 起为主归属 */
    private String stageKey;

    /** 服务于哪道门（↔ ck_gate.code）；技术门层已取消，仅老数据有值（前端取阶段时回落用） */
    private String gate;

    /** 评审类型 */
    private String type;

    /** 关联流程 key（评审流程，来自基础包） */
    private String processKey;

    /** 检查项清单（JSON 字符串） */
    private String checkItems;

    /** 应产出物类型（JSON 字符串） */
    private String outputTypes;

    /** 结论选项（JSON 字符串） */
    private String verdictOptions;

    private String industryNote;

    /** 溯源：从哪条模板快照而来 */
    private String templateCode;

    /** 执行态：PENDING / DONE / NA */
    private String status;

    /** 实际结论 */
    private String verdict;

    /** 结论说明 */
    private String verdictNote;

    /** 评审完成时间 */
    private java.time.LocalDateTime reviewedAt;

    /** 本项目裁剪：停用 */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    // ==================== 流程进展（传输字段，不落库） ====================

    /*
     * 评审实例本身只有 status / verdict 两列，发起流程后它们不会自己变 —— 变化在流程侧。
     * 下面这组字段由查询时按「流程实例 → 评审」现算填入（见 ReviewProcessEvaluator），
     * 与 Deliverable.ownerRoleName 同一套口径：只传输，不进 ck_review。
     *
     * 为什么不写回库：写回要靠流程回调，一旦漏发（终止、异常、历史清理）就永远停在"进行中"；
     * 而这里看的时候算，流程是唯一事实来源，页面永远与引擎一致。
     */

    /** 当前认领到这条评审的流程实例 id（点击「查看流程」用） */
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

    public Review() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }

    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }

    public String getCheckItems() { return checkItems; }
    public void setCheckItems(String checkItems) { this.checkItems = checkItems; }

    public String getOutputTypes() { return outputTypes; }
    public void setOutputTypes(String outputTypes) { this.outputTypes = outputTypes; }

    public String getVerdictOptions() { return verdictOptions; }
    public void setVerdictOptions(String verdictOptions) { this.verdictOptions = verdictOptions; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getVerdictNote() { return verdictNote; }
    public void setVerdictNote(String verdictNote) { this.verdictNote = verdictNote; }

    public java.time.LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(java.time.LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

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
}
