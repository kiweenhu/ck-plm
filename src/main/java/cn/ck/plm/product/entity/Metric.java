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
 * 指标实例：某个产品系列 / 型号上要算的那个数（健康度分数、成本达成率…）。
 *
 * <p>创建时从指标模板（{@code ck_metric_template}）快照而来 —— 快照的是<b>计算口径</b>
 * （layer / level / source），而 {@code value} / {@code readiness} 是<b>运行期评估结果</b>：
 * 数据没就绪时 {'readiness'} 为 C，此时界面显示"暂未评估"，<b>禁止编造数值</b>。
 */
public class Metric extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE / MODEL（见 {@link Stage#OWNER_TYPE_LINE}） */
    private String ownerType;

    /** 分层（看的层级 / 视角） */
    private String layer;

    /** 层级（企业 / 系列 / 型号） */
    private String level;

    /** 取数依据（口径说明） */
    private String source;

    private String industryNote;

    /** 溯源：从哪条模板快照而来 */
    private String templateCode;

    /** 评估结果（数值） */
    private java.math.BigDecimal value;

    /** 评估结果（文字，如"暂未评估"） */
    private String valueText;

    /** 数据就绪度：A / B / C（未评估则为空） */
    private String readiness;

    /** 评估时间 */
    private java.time.LocalDateTime evaluatedAt;

    /** 本项目裁剪：停用 */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public Metric() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public java.math.BigDecimal getValue() { return value; }
    public void setValue(java.math.BigDecimal value) { this.value = value; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }

    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness; }

    public java.time.LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(java.time.LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
