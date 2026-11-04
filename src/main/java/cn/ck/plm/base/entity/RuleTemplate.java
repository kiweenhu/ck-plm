/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 检查规则模板实体。
 *
 * <p>与阶段 / 交付件 / 门 / 评审模板同型：随包装入"企业一份"，企业可裁剪、可停用。
 *
 * <h3>一条规则 = 什么</h3>
 * 时机（{@link #triggerType}）+ 对象（{@link #scope}）+ 条件（{@link #condition}）
 * + 严重度（{@link #severity}）+ 提示（{@link #message}）；可选：分类、所属阶段/门、
 * 企业画像联动（{@link #defaultOverride}）、修复动作（{@link #fixAction}）。
 * <b>声明式 JSON，不是脚本</b> —— 规则能被审、能被企业改，运行期由引擎解释执行。
 *
 * <h3>四组枚举（安装器逐条校验，越界即 FAILED）</h3>
 * <pre>
 * triggerType  ∈ STAGE_ENTRY / PART_SAVE / BOM_SAVE / GATE_CHECK / RELEASE
 * scope        ∈ PRODUCT_MODEL / PART / BOM
 * condition.type ∈ FIELD_CHECK / FIELD_EMPTY / ISSUE_OPEN / OBJECT_MISSING
 * severity     ∈ BLOCK / WARN
 * </pre>
 *
 * <h3>三处引用</h3>
 * {@link #stageKey} ↔ {@code ck_stage.code}；{@link #gate} ↔ {@code ck_gate_template.code}；
 * {@link #fixAction} 里的 {@code code} 指向交付件（{@code ck_deliverable_template.code}）——
 * 如"缺认证证书 → 一键跳到那份交付件去补"。
 *
 * <h3>与"编码/版本规则"的区别</h3>
 * {@code ck_number_rule} / {@code ck_version_rule} 是<b>编码与版本号的生成规则</b>；
 * 本类是在对象保存 / 进阶段 / 过门 / 发布等时机做的<b>合规检查规则</b>。两个概念，别混。
 */
public class RuleTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用（基础包共用规则即空） */
    private String industry;

    /**
     * 检查时机：{@code STAGE_ENTRY} / {@code PART_SAVE} / {@code BOM_SAVE} / {@code GATE_CHECK} / {@code RELEASE}。
     * 属性名带 Type 后缀是刻意的：{@code trigger} 是 SQL 保留字，列名只能叫 {@code trigger_type}。
     */
    private String triggerType;

    /** 检查对象：{@code PRODUCT_MODEL} / {@code PART} / {@code BOM} */
    private String scope;

    /** 可选分类（如 battery） */
    private String category;

    /** 可选：所属研发阶段 → {@code ck_stage.code} */
    private String stageKey;

    /** 可选：所属阶段门 → {@code ck_gate_template.code} */
    private String gate;

    /**
     * 条件（JSON 字符串）。四种形态：
     * {@code {"type":"FIELD_CHECK","field":"…","operator":"NOT_IN","value":[…]}}、
     * {@code {"type":"FIELD_EMPTY","field":"…"}}、
     * {@code {"type":"ISSUE_OPEN","issueType":"…","minCount":1}}、
     * {@code {"type":"OBJECT_MISSING","objectType":"…","stage":"…"}}。
     */
    private String condition;

    /** 严重度：{@code BLOCK}（阻断）/ {@code WARN}（提示） */
    private String severity;

    /** 提示语（可含 {@code {model.code}} / {@code {part.code}} / {@code {count}} 占位符） */
    private String message;

    /** 企业画像联动（JSON）：{@code {"severity":"BLOCK","when":"exportMarket = true"}} */
    private String defaultOverride;

    /** 修复动作（JSON）：{@code {"type":"NAVIGATE","target":"deliverable","code":"DLV-CERT-REPORT"}} */
    private String fixAction;

    /** 行业注解：这条规则为什么这么定（讲行业道理的原文，给人看） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public RuleTemplate() {}

    public RuleTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

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

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
