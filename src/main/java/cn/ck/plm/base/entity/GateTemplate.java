/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 阶段门（评审门）模板实体。
 *
 * <p>与 {@link StageTemplate} / {@link DeliverableTemplate} 同型：由行业包随包装入"企业一份"，
 * 企业在自己的那份上裁剪（改决策选项 / 改参与者 / 改自动检查 / 停用），后续按模板实例化到
 * 具体产品系列或型号（门实例表待 P3 落地）。
 *
 * <h3>门分两类（这是文档定死的口径）</h3>
 * <ul>
 *   <li>{@code TECHNICAL_REVIEW}（TR）—— 技术评审：产出问题清单与整改项；</li>
 *   <li>{@code DECISION_REVIEW}（DCP）—— 决策评审：产出 Go / Hold / Terminate 与商业留痕。</li>
 * </ul>
 * 技术评审和决策评审必须分开建模：合并建模的结果一定是四不像，而且审厂时拿不出决策依据。
 *
 * <h3>三处关系</h3>
 * <ol>
 *   <li>{@link #stageKey} ↔ {@code ck_stage.code}：这道门属于哪个研发阶段；</li>
 *   <li>{@link #autoChecks} 里的 {@code DELIVERABLE_RATE + stage} 按"本阶段交付件完成率"判定
 *       → 故本表的 {@code (code, stage)} 与交付件的 {@code (gate, stage)} 应一一对应，可交叉校验；</li>
 *   <li>{@link #processKey} ↔ 包内 {@code processes/<key>.bpmn20.xml}（可落在<i>包链任一包</i>中，
 *       如 {@code technical-review} 在基础包 electronics-core）—— 安装器逐条校验，**不允许悬空引用**。</li>
 * </ol>
 *
 * <p>四个 JSON 数组字段（{@link #decisionOptions} / {@link #autoChecks} / {@link #requiredParticipants} /
 * {@link #recordFields}）在库里存 JSON 字符串：包里写可读数组，安装器序列化后落库
 * （与 {@code defaultFolders} / {@code completion_rule} 同款口径）。
 */
public class GateTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用 */
    private String industry;

    /** 所属研发阶段编码：→ {@code ck_stage.code} */
    private String stageKey;

    /** 门类型：{@code TECHNICAL_REVIEW}（技术评审）/ {@code DECISION_REVIEW}（决策评审） */
    private String type;

    /** 承载流程 key：→ 包内 {@code processes/<key>.bpmn20.xml}；无流程则 NULL */
    private String processKey;

    /** 决策选项（JSON 数组），如 {@code ["GO","GO_WITH_CONDITION","RECYCLE"]} */
    private String decisionOptions;

    /** 自动检查项（JSON 数组），如 {@code [{"type":"DELIVERABLE_RATE","stage":"…","minPercent":100,"severity":"BLOCK"}]} */
    private String autoChecks;

    /** 必备参与者（JSON 数组，角色码 → {@code ck_role.code}） */
    private String requiredParticipants;

    /** 留痕字段（JSON 数组），如 {@code ["decision","decisionBasis","riskList"]} */
    private String recordFields;

    /** 是否必须留证据材料 */
    private Boolean evidenceRequired;

    /** 行业注解：这道门为什么这么设（讲行业道理的原文，给人看） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public GateTemplate() {}

    public GateTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

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

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
