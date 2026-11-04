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
 * 检查规则实例：某个产品系列 / 型号自己的检查清单（R-SH-01 出口认证规则、通用件认定…）。
 *
 * <p>创建时从检查规则模板（{@code ck_rule_template}）快照而来。{@code condition} 是"什么算违规"的
 * 判定依据（快照，模板以后改了不回灌）；执行结果记在 {@code status} / {@code findings}。
 */
public class Rule extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE / MODEL（见 {@link Stage#OWNER_TYPE_LINE}） */
    private String ownerType;

    /** 触发时机（SAVE / CHECKIN / RELEASE…） */
    private String triggerType;

    /** 作用域 */
    private String scope;

    /** 规则分类（硬件 / 合规 / 通用件…） */
    private String category;

    /** 关联研发阶段 code（可空） */
    private String stageKey;

    /** 关联阶段门 code（可空） */
    private String gate;

    /** 判定条件（JSON / 表达式字符串） */
    private String condition;

    /** 严重度（BLOCK / WARN / INFO） */
    private String severity;

    /** 违规提示语 */
    private String message;

    /** 是否可被覆盖 */
    private String defaultOverride;

    /** 修复建议 */
    private String fixAction;

    private String industryNote;

    /** 溯源：从哪条模板快照而来 */
    private String templateCode;

    /** 执行态：PENDING / PASS / FAIL / NA */
    private String status;

    /** 最近一次检查时间 */
    private java.time.LocalDateTime lastCheckedAt;

    /** 检查发现（JSON 字符串） */
    private String findings;

    /** 本项目裁剪：停用 */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public Rule() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }

    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDefaultOverride() { return defaultOverride; }
    public void setDefaultOverride(String defaultOverride) { this.defaultOverride = defaultOverride; }

    public String getFixAction() { return fixAction; }
    public void setFixAction(String fixAction) { this.fixAction = fixAction; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(java.time.LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
