/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 问题类型模板实体。
 *
 * <p>与前面五类模板同型：随包装入"企业一份"，企业可裁剪、可停用。
 *
 * <h3>为什么问题类型必须是独立数据</h3>
 * "问题闭环率""超期问题数"是健康度报告里最有说服力的指标，而它们只有在<b>问题被结构化</b>
 * 之后才算得出来 —— 塞进流程表单里就废了。故本表是这套方法论里"问题可以统计"的地基。
 *
 * <h3>三个关键字段</h3>
 * <ul>
 *   <li>{@link #defaultSeverity}：默认严重度，{@code BLOCK} / {@code WARN} —— 与检查规则、
 *       阶段门同一口径（一个概念一个词）；</li>
 *   <li>{@link #requireRootCause}：是否必须做根因分析（设计缺陷这类"不找根因改完还会再犯"的问题）；</li>
 *   <li>{@link #requireRetest}：是否必须复测（测试未通过这类"改完必须重新测过"的问题）。</li>
 * </ul>
 *
 * <h3>反向引用</h3>
 * 检查规则的 {@code condition.issueType}（如 R-SH-03 只认 {@code TEST_FAIL}）、
 * 门的 {@code ISSUE_OPEN} 自动检查，都可能按本表编码做统计 —— 编码是这套体系里的公共词汇。
 */
public class IssueTypeTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用 */
    private String industry;

    /** 默认严重度：{@code BLOCK}（阻断）/ {@code WARN}（提示） */
    private String defaultSeverity;

    /** 是否必须做根因分析 */
    private Boolean requireRootCause;

    /** 是否必须复测 */
    private Boolean requireRetest;

    /** 行业注解（包内字段名是 {@code note}，安装器映射到本字段；与其它模板的 industryNote 同一含义） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public IssueTypeTemplate() {}

    public IssueTypeTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getDefaultSeverity() { return defaultSeverity; }
    public void setDefaultSeverity(String defaultSeverity) { this.defaultSeverity = defaultSeverity; }

    public Boolean getRequireRootCause() { return requireRootCause; }
    public void setRequireRootCause(Boolean requireRootCause) { this.requireRootCause = requireRootCause; }

    public Boolean getRequireRetest() { return requireRetest; }
    public void setRequireRetest(Boolean requireRetest) { this.requireRetest = requireRetest; }

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
