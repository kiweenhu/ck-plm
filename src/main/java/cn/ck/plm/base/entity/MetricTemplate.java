/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 指标（驾驶舱）模板实体。
 *
 * <p>与前面六类模板同型：随包装入"企业一份"，企业可裁剪、可停用。
 * 但有一处<b>本质不同</b>：指标不是"清单式内容"，而是<b>计算口径 + 数据就绪度</b> ——
 * 它回答的是"这个数能不能算"，而不只是"有没有这项内容"。
 *
 * <h3>分层（{@link #layer}）</h3>
 * {@code businessLayer} 给研发总监看（成本 / 器件 / 问题）；
 * {@code executiveLayer} 给老板看（上市 / 复用 / 质量成本）—— 与包内 metrics.json 的两个数组一一对应。
 *
 * <h3>数据就绪度（{@link #level}）是硬约束</h3>
 * A / B / C 三级，文档第 16 节原话：
 * "C 级指标第一版显示『暂未评估』，<b>禁止编造数值</b> —— 一个编出来的数字被客户当场问穿，
 * 整个健康度报告的可信度就归零了。"
 * 故本字段是"这个数能不能显示"的开关，不是装饰。
 *
 * <h3>取数依据（{@link #source}）是它与平台之间的接缝</h3>
 * A/B 级的来源应是平台或包内已落地之物（{@code BomCostCalculator} / {@code missingCostLines} /
 * {@code PartAlternate} / 器件属性…）；C 级的来源尚未落地（{@code ReviewIssue} 需评审引擎、
 * 上市时间偏差需项目实体）。就绪度与来源对不上，就是"编数值"的前兆。
 */
public class MetricTemplate extends WithoutVersionEntity implements TenantEntity {

    /** 行业/方法论标识：与 {@code ck_stage_template.industry} 同值；空 = 通用（基础包共用指标即空） */
    private String industry;

    /** 分层：{@code businessLayer}（业务层）/ {@code executiveLayer}（经营层） */
    private String layer;

    /** 数据就绪度：{@code A} / {@code B} / {@code C}（C 级只显示"暂未评估"，禁止编造数值） */
    private String level;

    /** 取数依据（如 BomCostCalculator / missingCostLines / PartAlternate / qualificationStatus） */
    private String source;

    /** 行业注解（包内字段名是 {@code note}，安装器映射到本字段；含"第一版显示暂未评估"这类说明） */
    private String industryNote;

    /** 企业裁剪：停用（保留行与幂等键；筛选时按需过滤） */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public MetricTemplate() {}

    public MetricTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

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
