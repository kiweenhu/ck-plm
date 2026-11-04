/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 交付件（交付物）模板实体。
 *
 * <p>与 {@link StageTemplate} 同型：由行业包随包装入"企业一份"，企业在自己的那份上裁剪
 * （改强制 / 改完成条件 / 改责任人 / <b>停用</b>），创建产品系列或型号时按本表<b>快照</b>
 * 出实例（见 P2 的 {@code ck_deliverable}）。
 *
 * <h3>与研发阶段的关系只有一列</h3>
 * {@link #stageKey} ↔ {@code ck_stage.code}；{@link #gate} 必须等于该阶段的门
 * （门按"本阶段交付件完成率"判定）。两者不一致时，判定永远算不清 —— 故安装与新增时都要守这条不变量。
 *
 * <h3>为什么"停用"而不是"删除"</h3>
 * {@link #disabled} 保留行与幂等键（{@code code + tenant_oid}）：企业停用某条后，重装行业包
 * 只会 SKIP，不会把它当成缺失又装回来，也不会把企业改过的值冲掉。删除则会让包重装时"复活"该条。
 *
 * <h3>为什么关键字段现在就要有</h3>
 * 实例是<b>快照</b>式（实例化即定型，模板后改不回灌）—— 凡是实例要用的字段
 * （完成条件 / 门 / 责任人 / 工期偏移 / 专业 / 强制）都必须在本表里，否则实例化时无从复制。
 */
public class DeliverableTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值（TRADITIONAL / IPD / MILITARY / AUTOMOTIVE） */
    private String industry;

    /** 所属研发阶段编码：→ {@code ck_stage.code}（如 IPD_CONCEPT） */
    private String stageKey;

    /** 专业：PRODUCT / SYSTEM / HARDWARE / SOFTWARE / MECHANICAL / TEST / QUALITY / SUPPLY / PROCESS … */
    private String discipline;

    /** 是否强制项：非强制项允许企业裁剪或在实例层标 N/A */
    private Boolean mandatory;

    /** 预期承担角色：→ {@code ck_role.code}（如 HW_ENGINEER / IPDT_LEADER） */
    private String ownerRole;

    /** 产出物类型（如 DOCUMENT / PART） */
    private String objectType;

    /** 绑定方式（如 DOCUMENT：绑定一份文档才算产出） */
    private String bindMode;

    /**
     * 完成判定条件（JSON 字符串，形如 {@code {"all":[{"type":"BOUND"},{"type":"LIFECYCLE","minState":"已评审"}]}}）。
     *
     * <p>存字符串而不存 jsonb：与 {@code ck_stage_template.default_folders} 同款 ——
     * 包里写的是真数组，安装器序列化后落库，读出来给界面与判定器（P3）用。
     */
    private String completionRule;

    /** 相对阶段结束日的工期偏移（负数=提前，0=阶段结束时） */
    private Integer dueOffsetDays;

    /** 阶段门（必须等于本阶段的门，如 TR3） */
    private String gate;

    /** 行业注解：这一条为什么这么定（讲行业道理的原文，给人看） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public DeliverableTemplate() {}

    public DeliverableTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }

    public String getDiscipline() { return discipline; }
    public void setDiscipline(String discipline) { this.discipline = discipline; }

    public Boolean getMandatory() { return mandatory; }
    public void setMandatory(Boolean mandatory) { this.mandatory = mandatory; }

    public String getOwnerRole() { return ownerRole; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }

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

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
