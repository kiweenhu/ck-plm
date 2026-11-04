/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 驾驶舱（布局）模板实体。
 *
 * <p>与前面七类模板同型：随包装入"企业一份"，企业可裁剪、可停用。但形状最特殊 ——
 * 包内 {@code dashboards.json} 是 {@code scopes[]} + 两个<b>包级键</b>，落库时摊平成一行一个视角：
 * <ul>
 *   <li>{@code primaryScopeType}（主视角）→ {@link #primaryScope} 标记，命中那条为 true；</li>
 *   <li>{@code generatePolicy}（{@code {视角 → 生成节奏}} 映射）→ {@link #generatePolicy}，逐行摊平。</li>
 * </ul>
 *
 * <h3>三个视角（文档第 17 节的口径）</h3>
 * 经营战报（给 GM ✓）/ 系列战况简报（GM + 研发总监 ✓）/ 型号作战地图（研发 / PM 的工作面 ✓）。
 *
 * <h3>widgets 是什么、不是什么</h3>
 * 分组件码的 JSON 数组。其中一部分<b>就是指标码</b>（{@code COST_ACHIEVE} 在 metrics.json 里）→
 * 可与 {@code ck_metric_template} 对账；其余（{@code MODEL_RANKING} / {@code STAGE_READINESS} /
 * {@code DELIVERABLE_MATRIX}…）是"渲染件"而非数据项 —— 目前<b>没有定义表</b>，别假装能校验其存在性。
 *
 * <p>文档的硬话记在这里：{@code MODEL_RANKING} / {@code MODEL_COMPARE_TABLE} 是必选件 ——
 * "系列层分数必然是平均数，而平均数是管理的大敌。把平均数拆开的唯一手段是排名。"
 */
public class DashboardTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用 */
    private String industry;

    /** 受众（GM / GM+RDD / RD/PM） */
    private String audience;

    /** 页数（可选） */
    private Integer pageCount;

    /** 分组件码（JSON 数组，如 ["HEALTH_SCORE_TREND","COST_ACHIEVE",…]） */
    private String widgets;

    /** 生成节奏（摊平自包级 generatePolicy 映射，如 MONTHLY_ALWAYS） */
    private String generatePolicy;

    /** 是否主视角（= 包级 primaryScopeType；一个包只应有一个 true） */
    private Boolean primaryScope;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public DashboardTemplate() {}

    public DashboardTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getWidgets() { return widgets; }
    public void setWidgets(String widgets) { this.widgets = widgets; }

    public String getGeneratePolicy() { return generatePolicy; }
    public void setGeneratePolicy(String generatePolicy) { this.generatePolicy = generatePolicy; }

    public Boolean getPrimaryScope() { return primaryScope; }
    public void setPrimaryScope(Boolean primaryScope) { this.primaryScope = primaryScope; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
