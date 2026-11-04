/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 评审模板实体。
 *
 * <p>与 {@link StageTemplate} / {@link DeliverableTemplate} / {@link GateTemplate} 同型：由行业包
 * 随包装入"企业一份"，企业在自己的那份上裁剪（改检查项、改结论选项、停用），
 * 后续按模板实例化出评审单（评审实例待后续版本落地）。
 *
 * <h3>一份评审模板 = 什么</h3>
 * 归属阶段（{@link #stageKey}，2026-10-29 起技术评审直接挂阶段）+ 逐条检查项
 * （{@link #checkItems}，每条带 BLOCK/WARN 级别）+ 输出（{@link #outputTypes}：问题清单 / 结论）
 * + 结论选项（{@link #verdictOptions}）。
 * 检查项的价值不在"有几条"，而在"写代码的人想不出来、教科书也抄不到"——如原理图评审的
 * 20% 降额与封装与实物库一致，来自"板子冒烟了"和"贴片厂说贴不上"两类真实事故。
 *
 * <h3>三处关系</h3>
 * <ol>
 *   <li>{@link #stageKey} ↔ {@code ck_stage.code}：这份评审挂在哪个阶段
 *       （老数据是 {@link #gate} ↔ {@code ck_gate_template.code}，两列都可空、同一行只有一列有值，
 *        取阶段时按 stageKey → gate→门阶段 回落）；</li>
 *   <li>{@link #processKey} ↔ 包内 {@code processes/<key>.bpmn20.xml}（可在包链任一包中）；</li>
 *   <li><b>反向引用</b>：交付件的 {@code completion_rule} 里 {@code REVIEW_PASSED.reviewTemplate}
 *       指向本表的 {@link #code} —— 交付件那条"完成条件"就是"这份评审通过"。</li>
 * </ol>
 */
public class ReviewTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用 */
    private String industry;

    /** 归属阶段编码：→ {@code ck_stage.code}（技术评审直接挂阶段） */
    private String stageKey;

    /** 归属门编码：→ {@code ck_gate_template.code}；技术门层已取消，仅老数据有值 */
    private String gate;

    /** 评审类型：{@code TECHNICAL}（技术评审）/ {@code DECISION}（决策类评审） */
    private String type;

    /** 承载流程 key：→ 包内 {@code processes/<key>.bpmn20.xml}；无流程则 NULL */
    private String processKey;

    /** 检查项（JSON 数组），如 {@code [{"seq":1,"text":"…","severity":"BLOCK"}]} */
    private String checkItems;

    /** 输出类型（JSON 数组）：{@code ISSUE_LIST}（问题清单）/ {@code VERDICT}（结论） */
    private String outputTypes;

    /** 结论选项（JSON 数组）：{@code PASS} / {@code PASS_WITH_CONDITION} / {@code FAIL} */
    private String verdictOptions;

    /** 行业注解：这份评审为什么这么设（讲行业道理的原文，给人看） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public ReviewTemplate() {}

    public ReviewTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

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

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
