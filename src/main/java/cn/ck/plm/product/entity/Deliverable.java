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
 * 交付件实例：某个产品系列 / 型号在某研发阶段上应当交付的东西。
 *
 * <p>创建系列 / 型号时，从本租户的交付件模板（{@code ck_deliverable_template}）<b>快照</b>而来 ——
 * 内容列（强制、完成条件、责任人、工期偏移…）在创建那一刻定型：模板以后改了不回灌，
 * 实例上的裁剪也不会被模板覆盖。与 {@link Stage} 同一套归属方式（ownerOid + ownerType）。
 *
 * <h3>与阶段、阶段门的关系</h3>
 * <ul>
 *   <li>{@code stageKey} ↔ {@link Stage#getCode()}：决定它挂在哪一段；</li>
 *   <li>{@code gate} 须等于该阶段的门（阶段门实例 {@link Gate} 的 code）。</li>
 * </ul>
 *
 * <h3>两套状态别混</h3>
 * {@code disabled} = 本项目裁剪（这一条不参与）；{@code status} = 执行态（PENDING/IN_PROGRESS/DONE/NA）。
 * 前者回答"要不要"，后者回答"做到哪了"。
 */
public class Deliverable extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE（产品系列）/ MODEL（产品型号），取值见 {@link Stage#OWNER_TYPE_LINE} */
    private String ownerType;

    /** 所属研发阶段 code（↔ ck_stage.code） */
    private String stageKey;

    /** 专业：PRODUCT / SYSTEM / HARDWARE / SOFTWARE / STRUCTURE / TEST / SUPPLY */
    private String discipline;

    /** 是否强制交付件 */
    private Boolean mandatory;

    /** 责任人（→ ck_role.code 角色码）；具体指派到人记在 owner */
    private String ownerRole;

    /**
     * 责任人角色的<b>显示名</b>（如「系统工程师」）—— <b>传输字段，不落库</b>。
     *
     * <p>库里只存 code（SYSTEM_ENGINEER）：界面直接显示 code 等于让用户读枚举。
     * 由查询时按 code 反查 {@code ck_role.name} 填入（见 MethodologyInstanceServiceImpl）。
     */
    private String ownerRoleName;

    /** 产出物类型（DOCUMENT / PART…） */
    private String objectType;

    /** 绑定方式（DOCUMENT…） */
    private String bindMode;

    /** 完成条件（JSON 字符串：{"all":[{type,…}]}） */
    private String completionRule;

    /** 相对阶段结束日的偏移（负数 = 提前） */
    private Integer dueOffsetDays;

    /** 阶段门（须等于本阶段的门） */
    private String gate;

    /** 行业注解（这一条为什么这么定） */
    private String industryNote;

    /** 溯源：从哪条模板快照而来（项目自建条目为 NULL） */
    private String templateCode;

    /** 执行态：PENDING / IN_PROGRESS / DONE / NA */
    private String status;

    /** 具体负责人 */
    private String owner;

    /** 计划完成日 */
    private java.time.LocalDate dueDate;

    /** 判定证据（谁 / 何时 / 按哪条判的，JSON 字符串） */
    private String evidence;

    /** 产出物关联（文档 / 零件 oid，JSON 字符串） */
    private String artifactRefs;

    /** 本项目裁剪：停用（不参与本项目，但保留行） */
    private Boolean disabled;

    private Integer sortOrder;

    /** 租户 oid（业务表：由租户拦截器注入，Mapper 不显式书写） */
    private String tenantOid;

    public Deliverable() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }

    public String getDiscipline() { return discipline; }
    public void setDiscipline(String discipline) { this.discipline = discipline; }

    public Boolean getMandatory() { return mandatory; }
    public void setMandatory(Boolean mandatory) { this.mandatory = mandatory; }

    public String getOwnerRole() { return ownerRole; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }

    public String getOwnerRoleName() { return ownerRoleName; }
    public void setOwnerRoleName(String ownerRoleName) { this.ownerRoleName = ownerRoleName; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getBindMode() { return bindMode; }
    public void setBindMode(String bindMode) { this.bindMode = bindMode; }

    public String getCompletionRule() { return completionRule; }
    public void setCompletionRule(String completionRule) { this.completionRule = completionRule; }

    public Integer getDueOffsetDays() { return dueOffsetDays; }
    public void setDueOffsetDays(Integer dueOffsetDays) { this.dueOffsetDays = dueOffsetDays; }

    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public java.time.LocalDate getDueDate() { return dueDate; }
    public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getArtifactRefs() { return artifactRefs; }
    public void setArtifactRefs(String artifactRefs) { this.artifactRefs = artifactRefs; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
